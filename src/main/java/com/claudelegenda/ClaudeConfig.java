package com.claudelegenda;

/** Настройки сюжета. Поменяйте координаты входа под свой мир. */
public final class ClaudeConfig {
    private ClaudeConfig() {}

    /** Координаты входа в секретную базу (они же пишутся в «Записке»). Высота определяется по поверхности. */
    public static final int ENTRANCE_X = 1000;
    public static final int ENTRANCE_Z = 1000;

    /** На каком расстоянии от входа (в блоках) база строится при первом приближении. */
    public static final int BUILD_RADIUS = 80;

    /** Глубина комнаты под поверхностью. */
    public static final int DEPTH = 14;

    public static final int SLOWNESS_AMPLIFIER = 0;
    /** Regeneration IV: лечит быстрее, чем наносит урон утопление. */
    public static final int REGENERATION_AMPLIFIER = 3;
}
