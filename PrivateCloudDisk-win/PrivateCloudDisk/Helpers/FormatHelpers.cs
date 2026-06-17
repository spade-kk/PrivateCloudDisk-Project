namespace PrivateCloudDisk.Helpers;

/// <summary>
/// 文件大小格式化辅助类
/// </summary>
public static class FileSizeHelper
{
    public static string Format(long bytes)
    {
        return bytes switch
        {
            < 0 => "0 B",
            < 1024 => $"{bytes} B",
            < 1024 * 1024 => $"{bytes / 1024.0:F1} KB",
            < 1024 * 1024 * 1024 => $"{bytes / (1024.0 * 1024):F1} MB",
            _ => $"{bytes / (1024.0 * 1024 * 1024):F2} GB"
        };
    }
}

/// <summary>
/// 文件类型辅助类
/// </summary>
public static class FileTypeHelper
{
    public static string GetIcon(string? fileName)
    {
        if (string.IsNullOrEmpty(fileName)) return "\uE8A5"; // 默认图标

        var ext = Path.GetExtension(fileName).ToLower();
        return ext switch
        {
            // 图片
            ".jpg" or ".jpeg" or ".png" or ".gif" or ".bmp" or ".svg" or ".webp" => "\uEB9F",
            // 视频
            ".mp4" or ".avi" or ".mkv" or ".mov" or ".wmv" or ".flv" => "\uE8B2",
            // 音频
            ".mp3" or ".wav" or ".flac" or ".aac" or ".ogg" => "\uE8D6",
            // 文档
            ".pdf" => "\uEA90",
            ".doc" or ".docx" => "\uE8A5",
            ".xls" or ".xlsx" => "\uE9F9",
            ".ppt" or ".pptx" => "\uE902",
            ".txt" => "\uE8A5",
            // 压缩包
            ".zip" or ".rar" or ".7z" or ".tar" or ".gz" => "\uF012",
            // 代码
            ".cs" or ".java" or ".py" or ".js" or ".ts" or ".html" or ".css" => "\uE943",
            _ => "\uE8A5"
        };
    }

    public static string GetCategory(string? fileName)
    {
        if (string.IsNullOrEmpty(fileName)) return "OTHER";

        var ext = Path.GetExtension(fileName).ToLower();
        return ext switch
        {
            ".jpg" or ".jpeg" or ".png" or ".gif" or ".bmp" or ".svg" or ".webp" => "IMAGE",
            ".mp4" or ".avi" or ".mkv" or ".mov" or ".wmv" or ".flv" => "VIDEO",
            ".mp3" or ".wav" or ".flac" or ".aac" or ".ogg" => "AUDIO",
            ".pdf" or ".doc" or ".docx" or ".xls" or ".xlsx" or ".ppt" or ".pptx" or ".txt" => "DOCUMENT",
            ".zip" or ".rar" or ".7z" or ".tar" or ".gz" => "ARCHIVE",
            _ => "OTHER"
        };
    }
}

/// <summary>
/// 时间格式化辅助类
/// </summary>
public static class DateTimeHelper
{
    public static string FormatRelative(DateTime? dateTime)
    {
        if (dateTime == null) return "未知";

        var span = DateTime.UtcNow - dateTime.Value.ToUniversalTime();
        return span.TotalSeconds switch
        {
            < 60 => "刚刚",
            < 3600 => $"{span.Minutes} 分钟前",
            < 86400 => $"{span.Hours} 小时前",
            < 2592000 => $"{span.Days} 天前",
            < 31536000 => $"{span.Days / 30} 个月前",
            _ => dateTime.Value.ToString("yyyy-MM-dd HH:mm")
        };
    }

    public static string FormatFull(DateTime? dateTime)
    {
        return dateTime?.ToString("yyyy-MM-dd HH:mm:ss") ?? "未知";
    }
}