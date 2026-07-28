package ani.sanin.settings.saving

import android.graphics.Color
import ani.sanin.connections.PendingDeletion
import ani.sanin.connections.PendingProgressUpdate
import ani.sanin.connections.comments.AuthResponse
import ani.sanin.connections.mal.MAL
import ani.sanin.media.SearchHistory
import ani.sanin.notifications.comment.CommentStore
import ani.sanin.notifications.subscription.SubscriptionStore
import ani.sanin.settings.saving.internal.Location
import ani.sanin.settings.saving.internal.Pref

enum class PrefName(val data: Pref) {
    //General
    SharedUserID(Pref(Location.General, Boolean::class, true)),

    NSFWExtension(Pref(Location.General, Boolean::class, true)),
    ContinueMedia(Pref(Location.General, Boolean::class, true)),
    SearchSources(Pref(Location.General, Boolean::class, false)),
    RecentlyListOnly(Pref(Location.General, Boolean::class, false)),

    SubscriptionCheckingNotifications(Pref(Location.General, Boolean::class, true)),
    NotificationPopup(Pref(Location.General, Boolean::class, true)),
    NotificationEpisodeAiring(Pref(Location.General, Boolean::class, true)),
    NotificationNewComment(Pref(Location.General, Boolean::class, true)),
    NotificationCompletedEpisode(Pref(Location.General, Boolean::class, true)),
    NotificationCompletedAnime(Pref(Location.General, Boolean::class, true)),
    NotificationNewFollower(Pref(Location.General, Boolean::class, true)),
    SubscriptionPromptAtEnd(Pref(Location.General, Boolean::class, true)),
    CheckUpdate(Pref(Location.General, Boolean::class, true)),
    VerboseLogging(Pref(Location.General, Boolean::class, false)),
    DohProvider(Pref(Location.General, Int::class, 0)),
    HidePrivate(Pref(Location.General, Boolean::class, false)),
    DefaultUserAgent(
        Pref(
            Location.General,
            String::class,
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36"
        )
    ),
    AnimeExtensionRepos(Pref(Location.General, Set::class, setOf("https://raw.githubusercontent.com/Confused-Creature-180/aniyomi-extensions/repo/index.min.json"))),

    AnimeSourcesOrder(Pref(Location.General, List::class, listOf<String>())),
    EnabledProviders(Pref(Location.General, Set::class, setOf<String>())),
    SortedAnimeSH(Pref(Location.General, List::class, listOf<SearchHistory>())),
    SortedCharacterSH(Pref(Location.General, List::class, listOf<SearchHistory>())),
    SortedStaffSH(Pref(Location.General, List::class, listOf<SearchHistory>())),
    SortedStudioSH(Pref(Location.General, List::class, listOf<SearchHistory>())),
    SortedUserSH(Pref(Location.General, List::class, listOf<SearchHistory>())),

    CommentNotificationInterval(Pref(Location.General, Int::class, 0)),
    AnilistNotificationInterval(Pref(Location.General, Int::class, 3)),
    UnreadUserNotifications(Pref(Location.General, Int::class, 0)),
    UnreadMediaNotifications(Pref(Location.General, Int::class, 0)),
    UnreadSubscriptionNotifications(Pref(Location.General, Int::class, 0)),
    SubscriptionNotificationInterval(Pref(Location.General, Int::class, 2)),
    LastAnilistNotificationId(Pref(Location.General, Int::class, 0)),
    AnilistFilteredTypes(Pref(Location.General, Set::class, setOf<String>())),
    UseAlarmManager(Pref(Location.General, Boolean::class, false)),
    IncludeAnimeList(Pref(Location.General, Boolean::class, true)),

    AdultOnly(Pref(Location.General, Boolean::class, false)),
    CommentsEnabled(Pref(Location.General, Int::class, 0)),
    EnableSocks5Proxy(Pref(Location.General, Boolean::class, false)),
    ProxyAuthEnabled(Pref(Location.General, Boolean::class, false)),

    AnilistNotifications(Pref(Location.General, Boolean::class, true)),
    EpisodeNotifications(Pref(Location.General, Boolean::class, true)),
    ListStatusNotification(Pref(Location.General, Boolean::class, true)),
    AutoSyncAniList(Pref(Location.General, Boolean::class, true)),
    UpdateProgressAutomatically(Pref(Location.General, Boolean::class, true)),
    AutoUpdateExtensions(Pref(Location.General, Boolean::class, true)),
    ConfirmPlayerExit(Pref(Location.General, Boolean::class, false)),
    TraktClientId(Pref(Location.General, String::class, "")),
    TraktCommentsEnabled(Pref(Location.General, Int::class, 0)),
    TraktAccessToken(Pref(Location.Protected, String::class, "")),
    TraktUsername(Pref(Location.Protected, String::class, "")),

    //User Interface
    EpisodeMetadataSource(Pref(Location.UI, Int::class, 0)),

    UseCustomTheme(Pref(Location.UI, Boolean::class, false)),
    CustomThemeInt(Pref(Location.UI, Int::class, Color.parseColor("#039BE5"))),
    UseSourceTheme(Pref(Location.UI, Boolean::class, false)),
    UseMaterialYou(Pref(Location.UI, Boolean::class, false)),
    Theme(Pref(Location.UI, String::class, "BLUE")),
    SkipExtensionIcons(Pref(Location.UI, Boolean::class, false)),
    DarkMode(Pref(Location.UI, Int::class, 1)),
    AnimeDefaultView(Pref(Location.UI, Int::class, 0)),

    BlurBanners(Pref(Location.UI, Boolean::class, true)),
    BlurRadius(Pref(Location.UI, Float::class, 2f)),
    BlurSampling(Pref(Location.UI, Float::class, 2f)),
    ImmersiveMode(Pref(Location.UI, Boolean::class, false)),
    SmallView(Pref(Location.UI, Boolean::class, true)),
    DefaultStartUpTab(Pref(Location.UI, Int::class, 0)),
    HomeLayout(
        Pref(
            Location.UI,
            List::class,
            listOf(true, true, true, true, false, true)
        )
    ),
    HomeLayoutOrder(
        Pref(
            Location.UI,
            List::class,
            listOf(0, 1, 2, 3, 4, 5)
        )
    ),
    BannerAnimations(Pref(Location.UI, Boolean::class, true)),
    LayoutAnimations(Pref(Location.UI, Boolean::class, true)),
    TrendingScroller(Pref(Location.UI, Boolean::class, true)),
    AnimationSpeed(Pref(Location.UI, Float::class, 1f)),
    ListGrid(Pref(Location.UI, Boolean::class, true)),
    PopularAnimeList(Pref(Location.UI, Boolean::class, true)),
    AnimeListSortOrder(Pref(Location.UI, String::class, "score")),

    CommentSortOrder(Pref(Location.UI, String::class, "newest")),
    FollowerLayout(Pref(Location.UI, Int::class, 0)),
    ShowNotificationRedDot(Pref(Location.UI, Boolean::class, true)),

    OledMode(Pref(Location.UI, Int::class, 1)),
    GradientDirection(Pref(Location.UI, Int::class, 0)),
    Emoji(Pref(Location.UI, Boolean::class, true)),
    AnimationsEnabled(Pref(Location.UI, Boolean::class, true)),
    LiveSideRail(Pref(Location.UI, Boolean::class, true)),
    PlayerGestureAnimations(Pref(Location.UI, Boolean::class, true)),
    PlayerControllerAnimations(Pref(Location.UI, Boolean::class, true)),
    ProfileAnimations(Pref(Location.UI, Boolean::class, true)),
    HomeAnimations(Pref(Location.UI, Boolean::class, true)),

    FocusAnimations(Pref(Location.UI, Boolean::class, true)),
    KeyboardKeyAnimations(Pref(Location.UI, Boolean::class, true)),
    TransitionAnimations(Pref(Location.UI, Boolean::class, true)),
    PlayerOverlayAnimations(Pref(Location.UI, Boolean::class, true)),
    DoubleTapAnimations(Pref(Location.UI, Boolean::class, true)),
    SeekBarAnimations(Pref(Location.UI, Boolean::class, true)),
    ProgressShakeAnimations(Pref(Location.UI, Boolean::class, true)),
    MiscUiAnimations(Pref(Location.UI, Boolean::class, true)),
    SplashAnimations(Pref(Location.UI, Boolean::class, true)),
    LikeButtonAnimations(Pref(Location.UI, Boolean::class, true)),
    IncognitoBannerAnimations(Pref(Location.UI, Boolean::class, true)),
    DescriptionExpandAnimations(Pref(Location.UI, Boolean::class, true)),
    SearchHeaderAnimations(Pref(Location.UI, Boolean::class, true)),
    ScrollToTopAnimations(Pref(Location.UI, Boolean::class, true)),
    InstallSpinnerAnimations(Pref(Location.UI, Boolean::class, true)),
    FilterResetAnimations(Pref(Location.UI, Boolean::class, true)),
    XpandableAnimations(Pref(Location.UI, Boolean::class, true)),
    InfoPageAnimations(Pref(Location.UI, Boolean::class, true)),
    NoInternetAnimations(Pref(Location.UI, Boolean::class, true)),
    ImageDialogAnimations(Pref(Location.UI, Boolean::class, true)),
    NotificationPopupAnimations(Pref(Location.UI, Boolean::class, true)),
    CommentInputAnimations(Pref(Location.UI, Boolean::class, true)),
    NavRailAnimations(Pref(Location.UI, Boolean::class, true)),
    AnimatedVectorDrawables(Pref(Location.UI, Boolean::class, true)),
    SideRailPersist(Pref(Location.UI, Boolean::class, false)),
    SideRailAutoOrientation(Pref(Location.UI, Boolean::class, true)),
    FirstTimeProviderShown(Pref(Location.General, Boolean::class, false)),
    NavPillHeight(Pref(Location.UI, Int::class, 58)),
    NavPillWidth(Pref(Location.UI, Int::class, 59)),
    NavPillSpacing(Pref(Location.UI, Int::class, 26)),
    NavPillIconSize(Pref(Location.UI, Int::class, 23)),
    NavPillIconColor(Pref(Location.UI, Int::class, 0xFFFFFFFF.toInt())),
    NavPillCornerRadius(Pref(Location.UI, Int::class, 18)),
    BlurUnwatchedEpisodes(Pref(Location.UI, Boolean::class, false)),
    GreyWatchedEpisodes(Pref(Location.UI, Boolean::class, false)),
    FocusEffect(Pref(Location.UI, Int::class, 0)),
    CardStyle(Pref(Location.UI, Int::class, 0)),
    UseCustomKeyboard(Pref(Location.UI, Boolean::class, true)),
    KeyboardMode(Pref(Location.UI, Int::class, 2)), // 0=System, 1=ToggleButton (hidden), 2=Custom

    UIScale(Pref(Location.UI, Float::class, 1.0f)),

    AccentColor(Pref(Location.UI, Int::class, 0)),


    CardOrientation(Pref(Location.UI, Int::class, 1)),

    StandardCardRoundness(Pref(Location.UI, Int::class, 50)),
    ContinueWatchingCardRoundness(Pref(Location.UI, Int::class, 60)),
    CardTitlePosition(Pref(Location.UI, Int::class, 0)),
    CardSize(Pref(Location.UI, Float::class, 1.5f)),
    BannerBrightness(Pref(Location.UI, Float::class, 1.0f)),
    ShowNewEpisodeBadge(Pref(Location.UI, Boolean::class, true)),
    ShowReleasingIndicator(Pref(Location.UI, Boolean::class, true)),
    CardGradientIntensity(Pref(Location.UI, Float::class, 0.7f)),

    // Glass Effect
    GlassEffectEnabled(Pref(Location.UI, Boolean::class, true)),
    GlassEffectNavPills(Pref(Location.UI, Boolean::class, true)),
    GlassEffectSideRail(Pref(Location.UI, Boolean::class, true)),
    GlassEffectServerSheet(Pref(Location.UI, Boolean::class, true)),
    GlassEffectListEditor(Pref(Location.UI, Boolean::class, true)),
    GlassEffectSourceSelector(Pref(Location.UI, Boolean::class, true)),
    GlassEffectEpisodeDrawer(Pref(Location.UI, Boolean::class, true)),
    GlassEffectSubtitleSync(Pref(Location.UI, Boolean::class, true)),
    GlassEffectBlurRadius(Pref(Location.UI, Float::class, 25f)),
    GlassEffectTintOpacity(Pref(Location.UI, Float::class, 0.4f)),
    GlassEffectVibrancy(Pref(Location.UI, Float::class, 1.0f)),
    GlassEffectRefractionHeight(Pref(Location.UI, Float::class, 0.0f)),
    GlassEffectRefractionAmount(Pref(Location.UI, Float::class, 0.0f)),
    GlassEffectChromaticAberration(Pref(Location.UI, Float::class, 0.0f)),
    GlassEffectDepth(Pref(Location.UI, Boolean::class, false)),
    GlassEffectSurfaceTint(Pref(Location.UI, Int::class, Color.parseColor("#000000"))),
    GlassEffectTextColor(Pref(Location.UI, Int::class, Color.WHITE)),

    //Home
    HomeBannerMode(Pref(Location.UI, Int::class, 2)),
    HeroCardImage(Pref(Location.UI, Boolean::class, false)),
    ShowContinueWatching(Pref(Location.UI, Boolean::class, true)),
    ShowPlanned(Pref(Location.UI, Boolean::class, true)),
    ShowRecommendations(Pref(Location.UI, Boolean::class, true)),
    ShowTrending(Pref(Location.UI, Boolean::class, true)),
    ShowPopular(Pref(Location.UI, Boolean::class, true)),
    ShowRecent(Pref(Location.UI, Boolean::class, true)),

    //Player
    DefaultSpeed(Pref(Location.Player, Int::class, 5)),
    CursedSpeeds(Pref(Location.Player, Boolean::class, false)),
    Resize(Pref(Location.Player, Int::class, 0)),
    Subtitles(Pref(Location.Player, Boolean::class, true)),
    OnlineSubtitlesEnabled(Pref(Location.Player, Boolean::class, true)),
    OnlineSubtitleProviders(Pref(Location.Player, Set::class, setOf("Wyzie", "Stremio", "OpenSubtitles"))),
    OnlineSubtitleLanguages(Pref(Location.Player, Set::class, setOf("English"))),
    TextviewSubtitles(Pref(Location.Player, Boolean::class, false)),
    SubtitleDelay(Pref(Location.Player, Long::class, 0L)),
    SubtitleSyncEnabled(Pref(Location.Player, Boolean::class, false)),
    SubLanguage(Pref(Location.Player, Int::class, 9)),
    PrimaryColor(Pref(Location.Player, Int::class, Color.WHITE)),
    SecondaryColor(Pref(Location.Player, Int::class, Color.BLACK)),
    Outline(Pref(Location.Player, Int::class, 0)),
    SubBackground(Pref(Location.Player, Int::class, Color.TRANSPARENT)),
    SubWindow(Pref(Location.Player, Int::class, Color.TRANSPARENT)),
    SubAlpha(Pref(Location.Player, Float::class, 1f)),
    SubStroke(Pref(Location.Player, Float::class, 8f)),
    SubBottomMargin(Pref(Location.Player, Float::class, 1f)),
    Font(Pref(Location.Player, Int::class, 0)),
    FontSize(Pref(Location.Player, Int::class, 20)),
    Locale(Pref(Location.Player, Int::class, 2)),
    TimeStampsEnabled(Pref(Location.Player, Boolean::class, true)),
    AutoHideTimeStamps(Pref(Location.Player, Boolean::class, true)),
    UseProxyForTimeStamps(Pref(Location.Player, Boolean::class, false)),
    ShowTimeStampButton(Pref(Location.Player, Boolean::class, true)),
    AutoSkipOPED(Pref(Location.Player, Boolean::class, false)),
    AutoSkipRecap(Pref(Location.Player, Boolean::class, false)),
    AutoPlay(Pref(Location.Player, Boolean::class, true)),
    AutoSkipFiller(Pref(Location.Player, Boolean::class, false)),
    AskIndividualPlayer(Pref(Location.Player, Boolean::class, true)),
    ChapterZeroPlayer(Pref(Location.Player, Boolean::class, true)),
    UpdateForHPlayer(Pref(Location.Player, Boolean::class, false)),
    WatchPercentage(Pref(Location.Player, Float::class, 0.8f)),
    AlwaysContinue(Pref(Location.Player, Boolean::class, true)),
    FocusPause(Pref(Location.Player, Boolean::class, true)),
    Gestures(Pref(Location.Player, Boolean::class, true)),
    DoubleTap(Pref(Location.Player, Boolean::class, true)),
    FastForward(Pref(Location.Player, Boolean::class, true)),
    SeekTime(Pref(Location.Player, Int::class, 10)),
    SeekSensitivity(Pref(Location.Player, Int::class, 100)),
    SkipTime(Pref(Location.Player, Int::class, 85)),
    Pip(Pref(Location.Player, Boolean::class, true)),
    UseAdditionalCodec(Pref(Location.Player, Boolean::class, false)),
    PauseOverlay(Pref(Location.Player, Boolean::class, true)),
    ContinueWatchingShowScreenshot(Pref(Location.Player, Boolean::class, false)),
    SmartSourcePersistence(Pref(Location.General, Boolean::class, false)),
    PreferDub(Pref(Location.General, Boolean::class, false)),

    AutoHideTimeout(Pref(Location.Player, Int::class, 5)),
    BufferSize(Pref(Location.Player, Int::class, 32)),
    HardwareDecoding(Pref(Location.Player, Boolean::class, true)),

    GestureSliders(Pref(Location.Player, Boolean::class, true)),
    DpadEpisodeSkip(Pref(Location.Player, Boolean::class, true)),
    Interpolation(Pref(Location.Player, Boolean::class, false)),
    UpscalingAlgorithm(Pref(Location.Player, Int::class, 0)),
    RawConfiguration(Pref(Location.Player, String::class, "")),

    //Irrelevant
    Incognito(Pref(Location.Irrelevant, Boolean::class, false)),
    RescueMode(Pref(Location.Irrelevant, Boolean::class, false)),
    PendingProgressUpdates(Pref(Location.Irrelevant, List::class, listOf<PendingProgressUpdate>())),
    PendingDeletions(Pref(Location.Irrelevant, List::class, listOf<PendingDeletion>())),
    OfflineMode(Pref(Location.Irrelevant, Boolean::class, false)),

    DownloadsKeys(Pref(Location.Irrelevant, String::class, "")),

    ImageUrl(Pref(Location.Irrelevant, String::class, "")),
    AllowOpeningLinks(Pref(Location.Irrelevant, Boolean::class, false)),
    SearchStyle(Pref(Location.Irrelevant, Int::class, 0)),
    HasUpdatedPrefs(Pref(Location.Irrelevant, Boolean::class, false)),
    LangSort(Pref(Location.Irrelevant, String::class, "all")),
    GenresList(Pref(Location.Irrelevant, Set::class, setOf<String>())),
    TagsListIsAdult(Pref(Location.Irrelevant, Set::class, setOf<String>())),
    TagsListNonAdult(Pref(Location.Irrelevant, Set::class, setOf<String>())),
    MakeDefault(Pref(Location.Irrelevant, Boolean::class, true)),
    FirstComment(Pref(Location.Irrelevant, Boolean::class, true)),
    CommentAuthResponse(Pref(Location.Irrelevant, AuthResponse::class, "")),
    CommentTokenExpiry(Pref(Location.Irrelevant, Long::class, 0L)),
    LoggingEnabled(Pref(Location.UI, Boolean::class, false)),

    SmartTrim(Pref(Location.UI, Boolean::class, false)),
    CacheCapMb(Pref(Location.UI, Int::class, 100)),
    TrimIntervalMin(Pref(Location.UI, Int::class, 10)),
    TrimIntensity(Pref(Location.UI, Int::class, 80)),
    LogToFile(Pref(Location.Irrelevant, Boolean::class, false)),
    RecentGlobalNotification(Pref(Location.Irrelevant, Int::class, 0)),
    CommentNotificationStore(Pref(Location.Irrelevant, List::class, listOf<CommentStore>())),
    SubscriptionNotificationStore(
        Pref(
            Location.Irrelevant,
            List::class,
            listOf<SubscriptionStore>()
        )
    ),
    UnreadCommentNotifications(Pref(Location.Irrelevant, Int::class, 0)),
    DownloadsDir(Pref(Location.Irrelevant, String::class, "")),
    LocalDir(Pref(Location.Irrelevant, String::class, "")),
    OC(Pref(Location.Irrelevant, Boolean::class, false)),



    //Protected

    AnilistToken(Pref(Location.Protected, String::class, "")),
    AnilistAvatar(Pref(Location.Protected, String::class, "")),
    AnilistUserName(Pref(Location.Protected, String::class, "")),
    AnilistUserId(Pref(Location.Protected, String::class, "")),
    MALUserName(Pref(Location.Protected, String::class, "")),
    MALAvatar(Pref(Location.Protected, String::class, "")),
    MALCodeChallenge(Pref(Location.Protected, String::class, "")),
    MALToken(Pref(Location.Protected, MAL.ResponseToken::class, "")),
    AppPassword(Pref(Location.Protected, String::class, "")),
    BiometricToken(Pref(Location.Protected, String::class, "")),
    OverridePassword(Pref(Location.Protected, Boolean::class, false)),
    Socks5ProxyHost(Pref(Location.Protected, String::class, "")),
    Socks5ProxyPort(Pref(Location.Protected, String::class, "")),
    Socks5ProxyUsername(Pref(Location.Protected, String::class, "")),
    Socks5ProxyPassword(Pref(Location.Protected, String::class, "")),
}
