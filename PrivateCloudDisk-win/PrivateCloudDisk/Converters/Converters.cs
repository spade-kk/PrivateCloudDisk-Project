using System;
using System.Globalization;
using System.Windows;
using System.Windows.Data;

namespace PrivateCloudDisk.Converters;

/// <summary>
/// 文件大小格式化转换器
/// </summary>
public class FileSizeConverter : IValueConverter
{
    public object Convert(object value, Type targetType, object parameter, CultureInfo culture)
    {
        if (value is long bytes)
            return Helpers.FileSizeHelper.Format(bytes);
        if (value is int intBytes)
            return Helpers.FileSizeHelper.Format(intBytes);
        return "0 B";
    }

    public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture)
        => throw new NotImplementedException();
}

/// <summary>
/// 布尔值取反转换器
/// </summary>
public class InverseBoolConverter : IValueConverter
{
    public object Convert(object value, Type targetType, object parameter, CultureInfo culture)
        => value is bool b ? !b : true;

    public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture)
        => value is bool b ? !b : false;
}

/// <summary>
/// 布尔值转可见性转换器
/// </summary>
public class BoolToVisibilityConverter : IValueConverter
{
    public object Convert(object value, Type targetType, object parameter, CultureInfo culture)
    {
        bool invert = parameter is string s && s == "Invert";
        bool visible = value is bool b && b;
        if (invert) visible = !visible;
        return visible ? Visibility.Visible : Visibility.Collapsed;
    }

    public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture)
        => value is Visibility v && v == Visibility.Visible;
}

/// <summary>
/// 布尔值取反转可见性转换器
/// </summary>
public class InverseBoolToVisibilityConverter : IValueConverter
{
    public object Convert(object value, Type targetType, object parameter, CultureInfo culture)
    {
        bool invert = parameter is string s && s == "Invert";
        bool visible = value is bool b && b;
        if (invert) visible = !visible;
        return visible ? Visibility.Collapsed : Visibility.Visible;
    }

    public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture)
        => value is Visibility v && v == Visibility.Collapsed;
}

/// <summary>
/// 侧边栏宽度转换器 (折叠时50px，展开时220px)
/// </summary>
public class SidebarWidthConverter : IValueConverter
{
    public object Convert(object value, Type targetType, object parameter, CultureInfo culture)
    {
        bool isCollapsed = value is bool b && b;
        return isCollapsed ? new GridLength(60) : new GridLength(220);
    }

    public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture)
        => throw new NotImplementedException();
}

/// <summary>
/// 空字符串转可见性转换器 (非空显示)
/// </summary>
public class StringToVisibilityConverter : IValueConverter
{
    public object Convert(object value, Type targetType, object parameter, CultureInfo culture)
        => string.IsNullOrEmpty(value as string) ? Visibility.Collapsed : Visibility.Visible;

    public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture)
        => throw new NotImplementedException();
}

/// <summary>
/// 日期时间格式化转换器
/// </summary>
public class DateTimeFormatConverter : IValueConverter
{
    public object Convert(object value, Type targetType, object parameter, CultureInfo culture)
    {
        if (value is DateTime dt)
        {
            var format = parameter as string ?? "yyyy-MM-dd HH:mm";
            return dt.ToString(format);
        }
        return "未知";
    }

    public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture)
        => throw new NotImplementedException();
}

/// <summary>
/// 进度百分比格式化转换器 (0-1 转 "50%")
/// </summary>
public class PercentConverter : IValueConverter
{
    public object Convert(object value, Type targetType, object parameter, CultureInfo culture)
    {
        if (value is double d)
            return $"{d * 100:F0}%";
        return "0%";
    }

    public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture)
        => throw new NotImplementedException();
}

/// <summary>
/// 文件类型转图标转换器
/// </summary>
public class FileTypeToIconConverter : IValueConverter
{
    public object Convert(object value, Type targetType, object parameter, CultureInfo culture)
    {
        if (value is bool isDirectory)
            return isDirectory ? "Folder" : "File";
        
        if (value is string ext)
        {
            return ext.ToLowerInvariant() switch
            {
                ".pdf" => "FilePdf",
                ".doc" or ".docx" => "FileWord",
                ".xls" or ".xlsx" => "FileExcel",
                ".ppt" or ".pptx" => "FilePowerpoint",
                ".jpg" or ".jpeg" or ".png" or ".gif" or ".bmp" => "Image",
                ".mp3" or ".wav" or ".flac" => "Music",
                ".mp4" or ".avi" or ".mkv" or ".mov" => "Video",
                ".zip" or ".rar" or ".7z" => "Archive",
                ".txt" or ".md" => "FileAlt",
                _ => "File"
            };
        }
        return "File";
    }

    public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture)
        => throw new NotImplementedException();
}

/// <summary>
/// 文件大小智能格式化 (传入字节数)
/// </summary>
public class FileSizeFormatConverter : IValueConverter
{
    public object Convert(object value, Type targetType, object parameter, CultureInfo culture)
    {
        if (value is long size)
        {
            if (size < 1024) return $"{size} B";
            if (size < 1024 * 1024) return $"{size / 1024.0:F1} KB";
            if (size < 1024 * 1024 * 1024) return $"{size / 1024.0 / 1024.0:F1} MB";
            return $"{size / 1024.0 / 1024.0 / 1024.0:F2} GB";
        }
        return "0 B";
    }

    public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture)
        => throw new NotImplementedException();
}

/// <summary>
/// 百分比进度条颜色转换器
/// </summary>
public class ProgressColorConverter : IValueConverter
{
    public object Convert(object value, Type targetType, object parameter, CultureInfo culture)
    {
        if (value is double percent)
        {
            return percent < 90 ? Application.Current.Resources["PrimaryBrush"] 
                               : Application.Current.Resources["DangerBrush"];
        }
        return Application.Current.Resources["PrimaryBrush"];
    }

    public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture)
        => throw new NotImplementedException();
}

/// <summary>
/// 布尔值转网格视图图标 (List/Icons)
/// </summary>
public class BoolToGridIconConverter : IValueConverter
{
    public object Convert(object value, Type targetType, object parameter, CultureInfo culture)
    {
        bool isGridView = value is bool b && b;
        return isGridView ? FontAwesome.WPF.FontAwesomeIcon.List : FontAwesome.WPF.FontAwesomeIcon.Th;
    }

    public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture)
        => throw new NotImplementedException();
}

/// <summary>
/// 布尔值转文件夹图标 (FolderOpen/Folder)
/// </summary>
public class BoolToFolderIconConverter : IValueConverter
{
    public object Convert(object value, Type targetType, object parameter, CultureInfo culture)
    {
        bool isDirectory = value is bool b && b;
        return isDirectory ? FontAwesome.WPF.FontAwesomeIcon.FolderOpen : FontAwesome.WPF.FontAwesomeIcon.File;
    }

    public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture)
        => throw new NotImplementedException();
}

/// <summary>
/// 布尔值转文件夹图标颜色
/// </summary>
public class BoolToFolderColorConverter : IValueConverter
{
    public object Convert(object value, Type targetType, object parameter, CultureInfo culture)
    {
        bool isDirectory = value is bool b && b;
        if (isDirectory)
            return Application.Current.Resources["PrimaryBrush"];
        return Application.Current.Resources["TextSecondaryBrush"];
    }

    public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture)
        => throw new NotImplementedException();
}

/// <summary>
/// 布尔值登录标题转换器 (登录/注册)
/// </summary>
public class BoolToLoginTitleConverter : IValueConverter
{
    public object Convert(object value, Type targetType, object parameter, CultureInfo culture)
    {
        bool isRegisterMode = value is bool b && b;
        return isRegisterMode ? "注册账号" : "登录账号";
    }

    public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture)
        => throw new NotImplementedException();
}
