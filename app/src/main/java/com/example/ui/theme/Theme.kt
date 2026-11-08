package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * =========================================================================================
 * 8 РАЗНОЦВЕТНЫХ ТЕМ ОФОРМЛЕНИЯ: 4 ДНЕВНЫХ И 4 НОЧНЫХ
 * =========================================================================================
 */
enum class AppThemeMode(
    val displayNameRu: String,
    val displayNameDe: String,
    val isNight: Boolean,
    val previewPrimaryColor: Color,
    val previewBgColor: Color
) {
    // 4 ДНЕВНЫХ ТЕМЫ
    LIGHT_INDIGO(
        displayNameRu = "Индиго и Лазурь (День)",
        displayNameDe = "Indigo & Azur (Tag)",
        isNight = false,
        previewPrimaryColor = LightIndigoPrimary,
        previewBgColor = LightIndigoBackground
    ),
    LIGHT_EMERALD(
        displayNameRu = "Изумруд и Мята (День)",
        displayNameDe = "Smaragd & Minze (Tag)",
        isNight = false,
        previewPrimaryColor = LightEmeraldPrimary,
        previewBgColor = LightEmeraldBackground
    ),
    LIGHT_AMBER(
        displayNameRu = "Янтарь и Закат (День)",
        displayNameDe = "Bernstein & Sonnenuntergang (Tag)",
        isNight = false,
        previewPrimaryColor = LightAmberPrimary,
        previewBgColor = LightAmberBackground
    ),
    LIGHT_AMETHYST(
        displayNameRu = "Аметист и Лаванда (День)",
        displayNameDe = "Amethyst & Lavendel (Tag)",
        isNight = false,
        previewPrimaryColor = LightAmethystPrimary,
        previewBgColor = LightAmethystBackground
    ),

    // 4 НОЧНЫХ ТЕМЫ
    DARK_SPACE(
        displayNameRu = "Глубокий Космос (Ночь)",
        displayNameDe = "Tiefer Weltraum (Nacht)",
        isNight = true,
        previewPrimaryColor = DarkSpacePrimary,
        previewBgColor = DarkSpaceBackground
    ),
    DARK_CYBERPUNK(
        displayNameRu = "Киберпанк Неон (Ночь)",
        displayNameDe = "Cyberpunk Neon (Nacht)",
        isNight = true,
        previewPrimaryColor = DarkCyberPrimary,
        previewBgColor = DarkCyberBackground
    ),
    DARK_OBSIDIAN(
        displayNameRu = "Вулканический Обсидиан (Ночь)",
        displayNameDe = "Vulkanischer Obsidian (Nacht)",
        isNight = true,
        previewPrimaryColor = DarkObsidianPrimary,
        previewBgColor = DarkObsidianBackground
    ),
    DARK_AURORA(
        displayNameRu = "Северное Сияние (Ночь)",
        displayNameDe = "Nordlicht Aurora (Nacht)",
        isNight = true,
        previewPrimaryColor = DarkAuroraPrimary,
        previewBgColor = DarkAuroraBackground
    )
}

// 1. Дневная: Индиго
private val LightIndigoScheme = lightColorScheme(
    primary = LightIndigoPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8EAF6),
    onPrimaryContainer = LightIndigoPrimary,
    secondary = LightIndigoSecondary,
    background = LightIndigoBackground,
    onBackground = Color(0xFF141722),
    surface = LightIndigoSurface,
    onSurface = Color(0xFF141722),
    surfaceVariant = LightIndigoSurfaceVariant,
    onSurfaceVariant = Color(0xFF424754)
)

// 2. Дневная: Изумруд
private val LightEmeraldScheme = lightColorScheme(
    primary = LightEmeraldPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2E9),
    onPrimaryContainer = LightEmeraldPrimary,
    secondary = LightEmeraldSecondary,
    background = LightEmeraldBackground,
    onBackground = Color(0xFF131D16),
    surface = LightEmeraldSurface,
    onSurface = Color(0xFF131D16),
    surfaceVariant = LightEmeraldSurfaceVariant,
    onSurfaceVariant = Color(0xFF3F4D43)
)

// 3. Дневная: Янтарь
private val LightAmberScheme = lightColorScheme(
    primary = LightAmberPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFBE9E7),
    onPrimaryContainer = LightAmberPrimary,
    secondary = LightAmberSecondary,
    background = LightAmberBackground,
    onBackground = Color(0xFF221612),
    surface = LightAmberSurface,
    onSurface = Color(0xFF221612),
    surfaceVariant = LightAmberSurfaceVariant,
    onSurfaceVariant = Color(0xFF52443E)
)

// 4. Дневная: Аметист
private val LightAmethystScheme = lightColorScheme(
    primary = LightAmethystPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF3E5F5),
    onPrimaryContainer = LightAmethystPrimary,
    secondary = LightAmethystSecondary,
    background = LightAmethystBackground,
    onBackground = Color(0xFF1F1424),
    surface = LightAmethystSurface,
    onSurface = Color(0xFF1F1424),
    surfaceVariant = LightAmethystSurfaceVariant,
    onSurfaceVariant = Color(0xFF4C3E52)
)

// 5. Ночная: Глубокий Космос
private val DarkSpaceScheme = darkColorScheme(
    primary = DarkSpacePrimary,
    onPrimary = Color(0xFF001064),
    primaryContainer = Color(0xFF202B52),
    onPrimaryContainer = Color(0xFFE8EAF6),
    secondary = DarkSpaceSecondary,
    background = DarkSpaceBackground,
    onBackground = Color(0xFFEEF2F8),
    surface = DarkSpaceSurface,
    onSurface = Color(0xFFEEF2F8),
    surfaceVariant = DarkSpaceSurfaceVariant,
    onSurfaceVariant = Color(0xFFB4C0DC)
)

// 6. Ночная: Киберпанк Неон
private val DarkCyberScheme = darkColorScheme(
    primary = DarkCyberPrimary,
    onPrimary = Color(0xFF38004D),
    primaryContainer = Color(0xFF4A148C),
    onPrimaryContainer = Color(0xFFFFD7FC),
    secondary = DarkCyberSecondary,
    background = DarkCyberBackground,
    onBackground = Color(0xFFFCEEFF),
    surface = DarkCyberSurface,
    onSurface = Color(0xFFFCEEFF),
    surfaceVariant = DarkCyberSurfaceVariant,
    onSurfaceVariant = Color(0xFFD2B9E6)
)

// 7. Ночная: Вулканический Обсидиан
private val DarkObsidianScheme = darkColorScheme(
    primary = DarkObsidianPrimary,
    onPrimary = Color(0xFF4D0000),
    primaryContainer = Color(0xFF5C1A1A),
    onPrimaryContainer = Color(0xFFFFCDD2),
    secondary = DarkObsidianSecondary,
    background = DarkObsidianBackground,
    onBackground = Color(0xFFFAEEEE),
    surface = DarkObsidianSurface,
    onSurface = Color(0xFFFAEEEE),
    surfaceVariant = DarkObsidianSurfaceVariant,
    onSurfaceVariant = Color(0xFFD4B8B8)
)

// 8. Ночная: Северное Сияние
private val DarkAuroraScheme = darkColorScheme(
    primary = DarkAuroraPrimary,
    onPrimary = Color(0xFF003822),
    primaryContainer = Color(0xFF0B4D36),
    onPrimaryContainer = Color(0xFFD7FFEC),
    secondary = DarkAuroraSecondary,
    background = DarkAuroraBackground,
    onBackground = Color(0xFFE8FAF3),
    surface = DarkAuroraSurface,
    onSurface = Color(0xFFE8FAF3),
    surfaceVariant = DarkAuroraSurfaceVariant,
    onSurfaceVariant = Color(0xFFB1D8CB)
)

@Composable
fun VoxScribeTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK_SPACE,
    content: @Composable () -> Unit,
) {
    val colorScheme = when (themeMode) {
        AppThemeMode.LIGHT_INDIGO -> LightIndigoScheme
        AppThemeMode.LIGHT_EMERALD -> LightEmeraldScheme
        AppThemeMode.LIGHT_AMBER -> LightAmberScheme
        AppThemeMode.LIGHT_AMETHYST -> LightAmethystScheme
        AppThemeMode.DARK_SPACE -> DarkSpaceScheme
        AppThemeMode.DARK_CYBERPUNK -> DarkCyberScheme
        AppThemeMode.DARK_OBSIDIAN -> DarkObsidianScheme
        AppThemeMode.DARK_AURORA -> DarkAuroraScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
