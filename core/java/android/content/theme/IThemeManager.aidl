package android.content.theme;

interface IThemeManager {
    int getMaskedColor(String packageName, String resourceName, int defaultValue);
    boolean checkTheme(String packageName);
    void enableTheme(String packageName);
    void reloadAssets();
    List<String> getAllThemes();
    String getCurrentTheme();
}
