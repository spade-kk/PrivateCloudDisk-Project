using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Data;

namespace PrivateCloudDisk.Converters;

/// <summary>
/// 文件大小格式化转换器
/// </summary>
public class FileSizeConverter : IValueConverter
{
    public object Convert(object value, Type targetType, object parameter, string language)
    {
        if (value is long bytes)
            return Helpers.FileSizeHelper.Format(bytes);
        if (value is int intBytes)
            return Helpers.FileSizeHelper.Format(intBytes);
        return "0 B";
    }

    public object ConvertBack(object value, Type targetType, object parameter, string language)
        => throw new NotImplementedException();
}

/// <summary>
/// 布尔值取反转换器
/// </summary>
public class InverseBoolConverter : IValueConverter
{
    public object Convert(object value, Type targetType, object parameter, string language)
        => value is bool b ? !b : true;

    public object ConvertBack(object value, Type targetType, object parameter, string language)
        => value is bool b ? !b : false;
}

/// <summary>
/// 布尔值转可见性转换器
/// </summary>
public class BoolToVisibilityConverter : IValueConverter
{
    public object Convert(object value, Type targetType, object parameter, string language)
    {
        bool invert = parameter is string s && s == "Invert";
        bool visible = value is bool b && b;
        if (invert) visible = !visible;
        return visible ? Visibility.Visible : Visibility.Collapsed;
    }

    public object ConvertBack(object value, Type targetType, object parameter, string language)
        => value is Visibility v && v == Visibility.Visible;
}

/// <summary>
/// 空字符串转可见性转换器 (非空显示)
/// </summary>
public class StringToVisibilityConverter : IValueConverter
{
    public object Convert(object value, Type targetType, object parameter, string language)
        => string.IsNullOrEmpty(value as string) ? Visibility.Collapsed : Visibility.Visible;

    public object ConvertBack(object value, Type targetType, object parameter, string language)
        => throw new NotImplementedException();
}

/// <summary>
/// 日期时间格式化转换器
/// </summary>
public class DateTimeFormatConverter : IValueConverter
{
    public object Convert(object value, Type targetType, object parameter, string language)
    {
        if (value is DateTime dt)
        {
            var format = parameter as string ?? "yyyy-MM-dd HH:mm";
            return dt.ToString(format);
        }
        return "未知";
    }

    public object ConvertBack(object value, Type targetType, object parameter, string language)
        => throw new NotImplementedException();
}

/// <summary>
/// 进度百分比格式化转换器 (0-1 转 "50%")
/// </summary>
public class PercentConverter : IValueConverter
{
    public object Convert(object value, Type targetType, object parameter, string language)
    {
        if (value is double d)
            return $"{d * 100:F0}%";
        return "0%";
    }

    public object ConvertBack(object value, Type targetType, object parameter, string language)
        => throw new NotImplementedException();
}