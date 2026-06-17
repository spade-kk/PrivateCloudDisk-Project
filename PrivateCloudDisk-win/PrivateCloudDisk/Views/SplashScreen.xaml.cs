using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Media.Animation;
using Microsoft.UI.Xaml.Shapes;
using PrivateCloudDisk.ViewModels;
using System;
using System.Threading.Tasks;
using Windows.Foundation;

namespace PrivateCloudDisk.Views;

/// <summary>
/// 应用启动闪屏页 — 品牌展示 + 后台初始化
/// 展示 Logo、品牌名称、特性描述，同时在后台加载认证状态和服务
/// </summary>
public sealed partial class SplashScreen : Page
{
    public SplashViewModel ViewModel { get; }

    public event Action? InitializationCompleted;

    public SplashScreen()
    {
        ViewModel = App.Services.GetRequiredService<SplashViewModel>();
        InitializeComponent();

        Loaded += OnLoaded;
        ViewModel.InitializationCompleted += OnInitCompleted;
    }

    private async void OnLoaded(object sender, RoutedEventArgs e)
    {
        // 启动动画
        LogoAnimation.Begin();
        BrandFadeIn.Begin();
        SubtitleFadeIn.Begin();
        DividerFadeIn.Begin();
        FeaturesFadeIn.Begin();
        LoadingFadeIn.Begin();

        // 绘制背景粒子
        DrawBackgroundParticles();

        // 开始后台初始化
        await ViewModel.StartInitializationAsync();
    }

    private void OnInitCompleted()
    {
        DispatcherQueue.TryEnqueue(() =>
        {
            // 播放退出动画后通知完成
            var storyboard = new Storyboard();

            var fadeOut = new DoubleAnimation
            {
                From = 1,
                To = 0,
                Duration = TimeSpan.FromMilliseconds(400),
                EasingFunction = new CubicEase { EasingMode = EasingMode.EaseIn }
            };
            Storyboard.SetTarget(fadeOut, this);
            Storyboard.SetTargetProperty(fadeOut, "Opacity");

            storyboard.Children.Add(fadeOut);
            storyboard.Completed += (s, _) =>
                InitializationCompleted?.Invoke();
            storyboard.Begin();
        });
    }

    /// <summary>
    /// 绘制装饰性背景粒子效果
    /// </summary>
    private void DrawBackgroundParticles()
    {
        var random = new Random(42); // 固定种子以保证一致性
        for (int i = 0; i < 30; i++)
        {
            var ellipse = new Ellipse
            {
                Width = random.Next(2, 6),
                Height = random.Next(2, 6),
                Fill = new SolidColorBrush(
                    Windows.UI.Color.FromArgb(
                        (byte)random.Next(20, 60),
                        (byte)random.Next(100, 200),
                        (byte)random.Next(150, 255),
                        (byte)random.Next(180, 255))),
                Opacity = random.NextDouble() * 0.5 + 0.2
            };

            Canvas.SetLeft(ellipse, random.NextDouble() * 800);
            Canvas.SetTop(ellipse, random.NextDouble() * 600);
            ParticlesCanvas.Children.Add(ellipse);

            // 为每个粒子创建浮动动画
            var storyboard = new Storyboard();
            var floatAnim = new DoubleAnimation
            {
                From = Canvas.GetTop(ellipse),
                To = Canvas.GetTop(ellipse) - random.Next(20, 60),
                Duration = TimeSpan.FromSeconds(random.Next(3, 8)),
                AutoReverse = true,
                RepeatBehavior = RepeatBehavior.Forever,
                EasingFunction = new SineEase()
            };
            Storyboard.SetTarget(floatAnim, ellipse);
            Storyboard.SetTargetProperty(floatAnim, "(Canvas.Top)");
            storyboard.Children.Add(floatAnim);
            storyboard.Begin();
        }
    }
}