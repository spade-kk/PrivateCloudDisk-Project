using System.Runtime.InteropServices;
using Windows.Storage;
using Windows.Storage.FileProperties;
using Windows.Storage.Streams;
using Microsoft.UI.Xaml.Media.Imaging;

namespace PrivateCloudDisk.Services.Implementations;

/// <summary>
/// Windows Shell 缩略图服务
///
/// 使用 Windows Shell API 提取文件缩略图：
///   - 图片缩略图（JPEG/PNG/GIF/BMP/WebP）
///   - 视频缩略图（MP4/AVI/MKV）
///   - PDF 封面
///   - Office 文档缩略图
///   - 文件夹缩略图（文件夹内图片拼贴）
///
/// 使用 IShellItemImageFactory 接口：
///   - 支持不同尺寸（SIIGBF_RESIZETOFIT）
///   - 缓存机制
/// </summary>
public class ThumbnailService
{
    private readonly Dictionary<string, (BitmapImage Image, DateTime CachedAt)> _cache = new();
    private readonly TimeSpan _cacheDuration = TimeSpan.FromMinutes(10);
    private readonly SemaphoreSlim _cacheLock = new(1, 1);

    // ── 公开方法 ──────────────────────────────────────────

    /// <summary>
    /// 获取文件缩略图
    /// </summary>
    /// <param name="filePath">文件路径</param>
    /// <param name="size">缩略图尺寸（像素）</param>
    /// <param name="useCache">是否使用缓存</param>
    public async Task<BitmapImage?> GetThumbnailAsync(
        string filePath, uint size = 256, bool useCache = true)
    {
        if (string.IsNullOrEmpty(filePath) || !File.Exists(filePath))
            return null;

        // 检查缓存
        if (useCache)
        {
            var cached = await GetCachedThumbnailAsync(filePath);
            if (cached != null) return cached;
        }

        BitmapImage? bitmap = null;

        try
        {
            // 方式 1：使用 StorageFile API（最简单，支持大多数格式）
            var storageFile = await StorageFile.GetFileFromPathAsync(filePath);

            var thumbnailMode = GetThumbnailMode(filePath);

            using var thumbnail = await storageFile.GetThumbnailAsync(
                thumbnailMode,
                size,
                ThumbnailOptions.UseCurrentScale);

            if (thumbnail != null && thumbnail.Type != ThumbnailType.Icon)
            {
                bitmap = new BitmapImage();
                await bitmap.SetSourceAsync(thumbnail);
            }
        }
        catch
        {
            // StorageFile API 失败，尝试 Shell API
        }

        // 方式 2：使用 IShellItemImageFactory（更底层，支持更多格式）
        if (bitmap == null)
        {
            bitmap = GetShellThumbnail(filePath, size);
        }

        // 缓存结果
        if (bitmap != null && useCache)
        {
            await CacheThumbnailAsync(filePath, bitmap);
        }

        return bitmap;
    }

    /// <summary>
    /// 批量获取缩略图
    /// </summary>
    public async Task<Dictionary<string, BitmapImage?>> GetThumbnailsAsync(
        IEnumerable<string> filePaths, uint size = 256)
    {
        var results = new Dictionary<string, BitmapImage?>();
        var tasks = filePaths.Select(async path =>
        {
            var thumbnail = await GetThumbnailAsync(path, size);
            lock (results)
            {
                results[path] = thumbnail;
            }
        });

        await Task.WhenAll(tasks);
        return results;
    }

    /// <summary>
    /// 获取文件夹缩略图（显示文件夹内图片拼贴）
    /// </summary>
    public async Task<BitmapImage?> GetFolderThumbnailAsync(
        string folderPath, uint size = 256)
    {
        if (!Directory.Exists(folderPath))
            return null;

        try
        {
            var folder = await StorageFolder.GetFolderFromPathAsync(folderPath);
            using var thumbnail = await folder.GetThumbnailAsync(
                ThumbnailMode.SingleItem, size, ThumbnailOptions.UseCurrentScale);

            if (thumbnail != null)
            {
                var bitmap = new BitmapImage();
                await bitmap.SetSourceAsync(thumbnail);
                return bitmap;
            }
        }
        catch { }

        return null;
    }

    /// <summary>
    /// 获取文件图标（非缩略图，文件类型图标）
    /// </summary>
    public static string GetFileIconGlyph(string extension)
    {
        return extension.ToLowerInvariant() switch
        {
            ".jpg" or ".jpeg" or ".png" or ".gif" or ".bmp" or ".webp" => "\uE722",
            ".mp4" or ".avi" or ".mkv" or ".mov" or ".wmv" => "\uE714",
            ".mp3" or ".wav" or ".flac" or ".aac" => "\uE8D6",
            ".pdf" => "\uE8A5",
            ".doc" or ".docx" => "\uE8A7",
            ".xls" or ".xlsx" => "\uE8A8",
            ".ppt" or ".pptx" => "\uE8A9",
            ".zip" or ".rar" or ".7z" => "\uE8B7",
            ".txt" => "\uE8A5",
            ".exe" or ".msi" => "\uE8B8",
            _ => "\uE8A5" // 默认文档图标
        };
    }

    // ── 缓存管理 ──────────────────────────────────────────

    private async Task<BitmapImage?> GetCachedThumbnailAsync(string filePath)
    {
        await _cacheLock.WaitAsync();
        try
        {
            if (_cache.TryGetValue(filePath, out var entry))
            {
                if (DateTime.UtcNow - entry.CachedAt < _cacheDuration)
                {
                    return entry.Image;
                }
                _cache.Remove(filePath);
            }
        }
        finally
        {
            _cacheLock.Release();
        }
        return null;
    }

    private async Task CacheThumbnailAsync(string filePath, BitmapImage image)
    {
        await _cacheLock.WaitAsync();
        try
        {
            _cache[filePath] = (image, DateTime.UtcNow);

            // 限制缓存大小
            if (_cache.Count > 500)
            {
                var oldest = _cache.OrderBy(kv => kv.Value.CachedAt).First();
                _cache.Remove(oldest.Key);
            }
        }
        finally
        {
            _cacheLock.Release();
        }
    }

    /// <summary>
    /// 清除缓存
    /// </summary>
    public async Task ClearCacheAsync()
    {
        await _cacheLock.WaitAsync();
        try
        {
            _cache.Clear();
        }
        finally
        {
            _cacheLock.Release();
        }
    }

    // ── Shell API 缩略图 ───────────────────────────────────

    private static BitmapImage? GetShellThumbnail(string filePath, uint size)
    {
        try
        {
            var result = NativeShell.SHCreateItemFromParsingName(
                filePath, IntPtr.Zero, typeof(NativeShell.IShellItemImageFactory).GUID,
                out var factory);

            if (result != 0 || factory == null)
                return null;

            var flags = NativeShell.SIIGBF.SIIGBF_RESIZETOFIT
                      | NativeShell.SIIGBF.SIIGBF_THUMBNAILONLY;

            result = factory.GetImage(new NativeShell.SIZE((int)size, (int)size), flags, out var hBitmap);

            if (result != 0 || hBitmap == IntPtr.Zero)
                return null;

            // 将 HBITMAP 转换为 BitmapImage
            return CreateBitmapFromHBitmap(hBitmap);
        }
        catch
        {
            return null;
        }
    }

    private static BitmapImage? CreateBitmapFromHBitmap(IntPtr hBitmap)
    {
        try
        {
            using var bitmap = System.Drawing.Image.FromHbitmap(hBitmap);
            using var stream = new MemoryStream();
            bitmap.Save(stream, System.Drawing.Imaging.ImageFormat.Png);
            stream.Position = 0;

            var bitmapImage = new BitmapImage();
            using var randomAccessStream = stream.AsRandomAccessStream();
            bitmapImage.SetSource(randomAccessStream);
            return bitmapImage;
        }
        catch
        {
            return null;
        }
        finally
        {
            NativeShell.DeleteObject(hBitmap);
        }
    }

    private static ThumbnailMode GetThumbnailMode(string filePath)
    {
        var ext = Path.GetExtension(filePath).ToLowerInvariant();

        return ext switch
        {
            ".jpg" or ".jpeg" or ".png" or ".gif" or ".bmp" or ".webp" => ThumbnailMode.PicturesView,
            ".mp4" or ".avi" or ".mkv" or ".mov" or ".wmv" => ThumbnailMode.VideosView,
            ".mp3" or ".wav" or ".flac" or ".aac" => ThumbnailMode.MusicView,
            ".pdf" or ".doc" or ".docx" or ".xls" or ".xlsx" or ".ppt" or ".pptx" =>
                ThumbnailMode.DocumentsView,
            _ => ThumbnailMode.SingleItem
        };
    }
}

// ────────────────────────────────────────────────────────
// Shell API P/Invoke
// ────────────────────────────────────────────────────────

internal static class NativeShell
{
    [Flags]
    public enum SIIGBF
    {
        SIIGBF_RESIZETOFIT = 0x00,
        SIIGBF_BIGGERSIZEOK = 0x01,
        SIIGBF_MEMORYONLY = 0x02,
        SIIGBF_ICONONLY = 0x04,
        SIIGBF_THUMBNAILONLY = 0x08,
        SIIGBF_INCACHEONLY = 0x10,
    }

    [StructLayout(LayoutKind.Sequential)]
    public struct SIZE
    {
        public int cx;
        public int cy;

        public SIZE(int cx, int cy)
        {
            this.cx = cx;
            this.cy = cy;
        }
    }

    [ComImport]
    [Guid("bcc18b79-ba16-442f-80c4-8a59c30c463b")]
    [InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    public interface IShellItemImageFactory
    {
        [PreserveSig]
        int GetImage(
            [In] SIZE size,
            [In] SIIGBF flags,
            [Out] out IntPtr phbm);
    }

    [DllImport("shell32.dll", CharSet = CharSet.Unicode, PreserveSig = true)]
    public static extern int SHCreateItemFromParsingName(
        [MarshalAs(UnmanagedType.LPWStr)] string pszPath,
        IntPtr pbc,
        [MarshalAs(UnmanagedType.LPStruct)] Guid riid,
        [MarshalAs(UnmanagedType.Interface)] out IShellItemImageFactory ppv);

    [DllImport("gdi32.dll")]
    [return: MarshalAs(UnmanagedType.Bool)]
    public static extern bool DeleteObject(IntPtr hObject);
}