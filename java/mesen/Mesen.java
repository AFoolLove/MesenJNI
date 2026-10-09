package mesen;

/**
 * Constants matching the MesenCore native enums. The int values correspond to
 * the C++ enum ordinals and must not be reordered independently.
 */
public final class Mesen
{
    private Mesen() {
    }

    // --- CpuType (Core/Shared/CpuType.h) -----------------------------------
    public static final int CPU_SNES = 0;
    public static final int CPU_SPC = 1;
    public static final int CPU_NEC_DSP = 2;
    public static final int CPU_SA1 = 3;
    public static final int CPU_GSU = 4;
    public static final int CPU_CX4 = 5;
    public static final int CPU_ST018 = 6;
    public static final int CPU_GAMEBOY = 7;
    public static final int CPU_NES = 8;
    public static final int CPU_PCE = 9;
    public static final int CPU_SMS = 10;
    public static final int CPU_GBA = 11;
    public static final int CPU_WS = 12;

    // --- ConsoleType (Core/Shared/SettingTypes.h) --------------------------
    public static final int CONSOLE_SNES = 0;
    public static final int CONSOLE_GAMEBOY = 1;
    public static final int CONSOLE_NES = 2;
    public static final int CONSOLE_PC_ENGINE = 3;
    public static final int CONSOLE_SMS = 4;
    public static final int CONSOLE_GBA = 5;
    public static final int CONSOLE_WS = 6;

    // --- RomFormat (Core/Shared/RomInfo.h) ---------------------------------
    public static final int ROM_FORMAT_UNKNOWN = 0;
    public static final int ROM_FORMAT_SFC = 1;
    public static final int ROM_FORMAT_SPC = 2;
    public static final int ROM_FORMAT_GB = 3;
    public static final int ROM_FORMAT_GBS = 4;
    public static final int ROM_FORMAT_INES = 5;
    public static final int ROM_FORMAT_UNIF = 6;
    public static final int ROM_FORMAT_FDS = 7;
    public static final int ROM_FORMAT_VS_SYSTEM = 8;
    public static final int ROM_FORMAT_VS_DUAL_SYSTEM = 9;
    public static final int ROM_FORMAT_NSF = 10;
    public static final int ROM_FORMAT_STUDY_BOX = 11;
    public static final int ROM_FORMAT_PCE = 12;
    public static final int ROM_FORMAT_PCE_CD_ROM = 13;
    public static final int ROM_FORMAT_PCE_HES = 14;
    public static final int ROM_FORMAT_SMS = 15;
    public static final int ROM_FORMAT_GAME_GEAR = 16;
    public static final int ROM_FORMAT_SG = 17;
    public static final int ROM_FORMAT_COLECOVISION = 18;
    public static final int ROM_FORMAT_GBA = 19;
    public static final int ROM_FORMAT_WS = 20;

    // --- HashType (Core/Shared/Emulator.h) ---------------------------------
    public static final int HASH_SHA1 = 0;
    public static final int HASH_SHA1_CHEAT = 1;

    // --- EmulationFlags (Core/Shared/SettingTypes.h, bit flags) ------------
    public static final int FLAG_TURBO = 0x01;
    public static final int FLAG_REWIND = 0x02;
    public static final int FLAG_TURBO_OR_REWIND = 0x03;
    public static final int FLAG_MAXIMUM_SPEED = 0x04;
    public static final int FLAG_IN_BACKGROUND = 0x08;
    public static final int FLAG_CONSOLE_MODE = 0x10;
    public static final int FLAG_TEST_MODE = 0x20;
    public static final int FLAG_OUTPUT_TO_STDOUT = 0x40;

    // --- MemoryType (Core/Shared/MemoryType.h) -----------------------------
    public static final int MEM_SNES = 0;
    public static final int MEM_SPC = 1;
    public static final int MEM_SA1 = 2;
    public static final int MEM_NEC_DSP = 3;
    public static final int MEM_GSU = 4;
    public static final int MEM_CX4 = 5;
    public static final int MEM_ST018 = 6;
    public static final int MEM_GAMEBOY = 7;
    public static final int MEM_NES = 8;
    public static final int MEM_NES_PPU = 9;
    public static final int MEM_PCE = 10;
    public static final int MEM_SMS = 11;
    public static final int MEM_GBA = 12;
    public static final int MEM_WS = 13;

    public static final int MEM_SNES_PRG_ROM = 14;
    public static final int MEM_SNES_WORK_RAM = 15;
    public static final int MEM_SNES_SAVE_RAM = 16;
    public static final int MEM_SNES_VIDEO_RAM = 17;
    public static final int MEM_SNES_SPRITE_RAM = 18;
    public static final int MEM_SNES_CG_RAM = 19;
    public static final int MEM_SNES_REGISTER = 20;
    public static final int MEM_SPC_RAM = 21;
    public static final int MEM_SPC_ROM = 22;
    public static final int MEM_SPC_DSP_REGISTERS = 23;
    public static final int MEM_DSP_PROGRAM_ROM = 24;
    public static final int MEM_DSP_DATA_ROM = 25;
    public static final int MEM_DSP_DATA_RAM = 26;
    public static final int MEM_SA1_INTERNAL_RAM = 27;
    public static final int MEM_GSU_WORK_RAM = 28;
    public static final int MEM_CX4_DATA_RAM = 29;
    public static final int MEM_BSX_PS_RAM = 30;
    public static final int MEM_BSX_MEMORY_PACK = 31;
    public static final int MEM_ST018_PRG_ROM = 32;
    public static final int MEM_ST018_DATA_ROM = 33;
    public static final int MEM_ST018_WORK_RAM = 34;
    public static final int MEM_SUFAMI_TURBO_FIRMWARE = 35;
    public static final int MEM_SUFAMI_TURBO_SECOND_CART = 36;
    public static final int MEM_SUFAMI_TURBO_SECOND_CART_RAM = 37;

    public static final int MEM_GB_PRG_ROM = 38;
    public static final int MEM_GB_WORK_RAM = 39;
    public static final int MEM_GB_CART_RAM = 40;
    public static final int MEM_GB_HIGH_RAM = 41;
    public static final int MEM_GB_BOOT_ROM = 42;
    public static final int MEM_GB_VIDEO_RAM = 43;
    public static final int MEM_GB_SPRITE_RAM = 44;
    public static final int MEM_GB_BG_PALETTE_RAM = 45;
    public static final int MEM_GB_OBJ_PALETTE_RAM = 46;

    public static final int MEM_NES_PRG_ROM = 47;
    public static final int MEM_NES_INTERNAL_RAM = 48;
    public static final int MEM_NES_WORK_RAM = 49;
    public static final int MEM_NES_SAVE_RAM = 50;
    public static final int MEM_NES_NAMETABLE_RAM = 51;
    public static final int MEM_NES_MAPPER_RAM = 52;
    public static final int MEM_NES_SPRITE_RAM = 53;
    public static final int MEM_NES_SECONDARY_SPRITE_RAM = 54;
    public static final int MEM_NES_PALETTE_RAM = 55;
    public static final int MEM_NES_CHR_RAM = 56;
    public static final int MEM_NES_CHR_ROM = 57;

    public static final int MEM_PCE_PRG_ROM = 58;
    public static final int MEM_PCE_WORK_RAM = 59;
    public static final int MEM_PCE_SAVE_RAM = 60;
    public static final int MEM_PCE_CDROM_RAM = 61;
    public static final int MEM_PCE_CARD_RAM = 62;
    public static final int MEM_PCE_ADPCM_RAM = 63;
    public static final int MEM_PCE_ARCADE_CARD_RAM = 64;
    public static final int MEM_PCE_VIDEO_RAM = 65;
    public static final int MEM_PCE_VIDEO_RAM_VDC2 = 66;
    public static final int MEM_PCE_SPRITE_RAM = 67;
    public static final int MEM_PCE_SPRITE_RAM_VDC2 = 68;
    public static final int MEM_PCE_PALETTE_RAM = 69;

    public static final int MEM_SMS_PRG_ROM = 70;
    public static final int MEM_SMS_WORK_RAM = 71;
    public static final int MEM_SMS_CART_RAM = 72;
    public static final int MEM_SMS_BOOT_ROM = 73;
    public static final int MEM_SMS_VIDEO_RAM = 74;
    public static final int MEM_SMS_PALETTE_RAM = 75;
    public static final int MEM_SMS_PORT = 76;

    public static final int MEM_GBA_PRG_ROM = 77;
    public static final int MEM_GBA_BOOT_ROM = 78;
    public static final int MEM_GBA_SAVE_RAM = 79;
    public static final int MEM_GBA_INT_WORK_RAM = 80;
    public static final int MEM_GBA_EXT_WORK_RAM = 81;
    public static final int MEM_GBA_VIDEO_RAM = 82;
    public static final int MEM_GBA_SPRITE_RAM = 83;
    public static final int MEM_GBA_PALETTE_RAM = 84;

    public static final int MEM_WS_PRG_ROM = 85;
    public static final int MEM_WS_WORK_RAM = 86;
    public static final int MEM_WS_CART_RAM = 87;
    public static final int MEM_WS_CART_EEPROM = 88;
    public static final int MEM_WS_BOOT_ROM = 89;
    public static final int MEM_WS_INTERNAL_EEPROM = 90;
    public static final int MEM_WS_PORT = 91;

    public static final int MEM_NONE = 92;

    // --- Controller buttons (KeyMapping field ids) -------------------------
    public static final int BUTTON_A = 0;
    public static final int BUTTON_B = 1;
    public static final int BUTTON_X = 2;
    public static final int BUTTON_Y = 3;
    public static final int BUTTON_L = 4;
    public static final int BUTTON_R = 5;
    public static final int BUTTON_UP = 6;
    public static final int BUTTON_DOWN = 7;
    public static final int BUTTON_LEFT = 8;
    public static final int BUTTON_RIGHT = 9;
    public static final int BUTTON_START = 10;
    public static final int BUTTON_SELECT = 11;
    public static final int BUTTON_U = 12;
    public static final int BUTTON_D = 13;
    public static final int BUTTON_TURBO_A = 14;
    public static final int BUTTON_TURBO_B = 15;
    public static final int BUTTON_TURBO_X = 16;
    public static final int BUTTON_TURBO_Y = 17;
    public static final int BUTTON_TURBO_L = 18;
    public static final int BUTTON_TURBO_R = 19;
    public static final int BUTTON_TURBO_SELECT = 20;
    public static final int BUTTON_TURBO_START = 21;

    // --- ControllerType (subset, Core/Shared/SettingTypes.h) ---------------
    public static final int CONTROLLER_NONE = 0;
    public static final int CONTROLLER_SNES = 1;
    public static final int CONTROLLER_SNES_MOUSE = 2;
    public static final int CONTROLLER_SUPER_SCOPE = 3;
    public static final int CONTROLLER_MULTITAP = 4;
    public static final int CONTROLLER_NES = 10;
    public static final int CONTROLLER_FAMICOM = 11;
    public static final int CONTROLLER_NES_ZAPPER = 13;
    public static final int CONTROLLER_FOUR_SCORE = 19;
    public static final int CONTROLLER_GAMEBOY = 42;
    public static final int CONTROLLER_PCE = 44;
    public static final int CONTROLLER_PCE_TURBO_TAP = 45;
    public static final int CONTROLLER_SMS = 47;
    public static final int CONTROLLER_GBA = 50;
    public static final int CONTROLLER_WS = 51;
    public static final int CONTROLLER_WS_VERTICAL = 52;

    // --- VideoFilterType (Core/Shared/SettingTypes.h) ----------------------
    public static final int VIDEO_FILTER_NONE = 0;
    public static final int VIDEO_FILTER_NTSC_BLARGG = 1;
    public static final int VIDEO_FILTER_NTSC_BISQWIT = 2;
    public static final int VIDEO_FILTER_LCD_GRID = 3;
    public static final int VIDEO_FILTER_XBRZ_2X = 4;
    public static final int VIDEO_FILTER_XBRZ_3X = 5;
    public static final int VIDEO_FILTER_XBRZ_4X = 6;
    public static final int VIDEO_FILTER_XBRZ_5X = 7;
    public static final int VIDEO_FILTER_XBRZ_6X = 8;
    public static final int VIDEO_FILTER_HQ_2X = 9;
    public static final int VIDEO_FILTER_HQ_3X = 10;
    public static final int VIDEO_FILTER_HQ_4X = 11;
    public static final int VIDEO_FILTER_SCALE_2X = 12;
    public static final int VIDEO_FILTER_SCALE_3X = 13;
    public static final int VIDEO_FILTER_SCALE_4X = 14;
    public static final int VIDEO_FILTER_2XSAI = 15;
    public static final int VIDEO_FILTER_SUPER_2XSAI = 16;
    public static final int VIDEO_FILTER_SUPER_EAGLE = 17;
    public static final int VIDEO_FILTER_PRESCALE_2X = 18;
    public static final int VIDEO_FILTER_PRESCALE_3X = 19;
    public static final int VIDEO_FILTER_PRESCALE_4X = 20;
    public static final int VIDEO_FILTER_PRESCALE_6X = 21;
    public static final int VIDEO_FILTER_PRESCALE_8X = 22;
    public static final int VIDEO_FILTER_PRESCALE_10X = 23;

    // --- VideoAspectRatio (Core/Shared/SettingTypes.h) ---------------------
    public static final int ASPECT_RATIO_NO_STRETCHING = 0;
    public static final int ASPECT_RATIO_AUTO = 1;
    public static final int ASPECT_RATIO_NTSC = 2;
    public static final int ASPECT_RATIO_PAL = 3;
    public static final int ASPECT_RATIO_STANDARD = 4;
    public static final int ASPECT_RATIO_WIDESCREEN = 5;
    public static final int ASPECT_RATIO_CUSTOM = 6;

    // --- ConsoleRegion (Core/Shared/SettingTypes.h) ------------------------
    public static final int REGION_AUTO = 0;
    public static final int REGION_NTSC = 1;
    public static final int REGION_PAL = 2;
    public static final int REGION_DENDY = 3;
    public static final int REGION_NTSC_JAPAN = 4;

    // --- DebuggerFlags (Core/Shared/SettingTypes.h, bit flags) -------------
    public static final int DEBUGGER_FLAG_SNES = 1 << 0;
    public static final int DEBUGGER_FLAG_SPC = 1 << 1;
    public static final int DEBUGGER_FLAG_SA1 = 1 << 2;
    public static final int DEBUGGER_FLAG_GSU = 1 << 3;
    public static final int DEBUGGER_FLAG_NEC_DSP = 1 << 4;
    public static final int DEBUGGER_FLAG_CX4 = 1 << 5;
    public static final int DEBUGGER_FLAG_ST018 = 1 << 6;
    public static final int DEBUGGER_FLAG_GB = 1 << 7;
    public static final int DEBUGGER_FLAG_NES = 1 << 8;
    public static final int DEBUGGER_FLAG_PCE = 1 << 9;
    public static final int DEBUGGER_FLAG_SMS = 1 << 10;
    public static final int DEBUGGER_FLAG_GBA = 1 << 11;
    public static final int DEBUGGER_FLAG_WS = 1 << 12;

    // --- ConsoleNotificationType (Core/Shared/Interfaces/INotificationListener.h)
    // 用于 NotificationListener.processNotification(int type) 的 type 参数。
    public static final int NOTIFICATION_GAME_LOADED = 0;
    public static final int NOTIFICATION_STATE_LOADED = 1;
    public static final int NOTIFICATION_GAME_RESET = 2;
    public static final int NOTIFICATION_GAME_PAUSED = 3;
    public static final int NOTIFICATION_GAME_RESUMED = 4;
    public static final int NOTIFICATION_CODE_BREAK = 5;
    public static final int NOTIFICATION_DEBUGGER_RESUMED = 6;
    public static final int NOTIFICATION_PPU_FRAME_DONE = 7;
    public static final int NOTIFICATION_RESOLUTION_CHANGED = 8;
    public static final int NOTIFICATION_EXECUTE_SHORTCUT = 9;
    public static final int NOTIFICATION_RELEASE_SHORTCUT = 10;
    public static final int NOTIFICATION_EMULATION_STOPPED = 11;
    public static final int NOTIFICATION_BEFORE_EMULATION_STOP = 12;
    public static final int NOTIFICATION_VIEWER_REFRESH = 13;
    public static final int NOTIFICATION_EVENT_VIEWER_REFRESH = 14;
    public static final int NOTIFICATION_MISSING_FIRMWARE = 15;
    public static final int NOTIFICATION_SUFAMI_TURBO_FILE_PROMPT = 16;
    public static final int NOTIFICATION_BEFORE_GAME_UNLOAD = 17;
    public static final int NOTIFICATION_BEFORE_GAME_LOAD = 18;
    public static final int NOTIFICATION_GAME_LOAD_FAILED = 19;
    public static final int NOTIFICATION_CHEATS_CHANGED = 20;
    public static final int NOTIFICATION_REQUEST_CONFIG_CHANGE = 21;
    public static final int NOTIFICATION_REFRESH_SOFTWARE_RENDERER = 22;
    public static final int NOTIFICATION_AFTER_INIT_CONSOLE = 23;
    public static final int NOTIFICATION_NETPLAY_STOPPED = 24;

    // --- 按钮名 -> BUTTON_* id ---------------------------------------------
    private static final java.util.Map<String, Integer> BUTTON_IDS = buildButtonIds();

    private static java.util.Map<String, Integer> buildButtonIds()
    {
        java.util.Map<String, Integer> m = new java.util.HashMap<>();
        m.put("A", BUTTON_A); m.put("B", BUTTON_B); m.put("X", BUTTON_X); m.put("Y", BUTTON_Y);
        m.put("L", BUTTON_L); m.put("R", BUTTON_R);
        m.put("Up", BUTTON_UP); m.put("Down", BUTTON_DOWN); m.put("Left", BUTTON_LEFT); m.put("Right", BUTTON_RIGHT);
        m.put("Start", BUTTON_START); m.put("Select", BUTTON_SELECT);
        m.put("U", BUTTON_U); m.put("D", BUTTON_D);
        m.put("TurboA", BUTTON_TURBO_A); m.put("TurboB", BUTTON_TURBO_B);
        m.put("TurboX", BUTTON_TURBO_X); m.put("TurboY", BUTTON_TURBO_Y);
        m.put("TurboL", BUTTON_TURBO_L); m.put("TurboR", BUTTON_TURBO_R);
        m.put("TurboSelect", BUTTON_TURBO_SELECT); m.put("TurboStart", BUTTON_TURBO_START);
        return m;
    }

    /** 把按钮名（如 "Start"）转成 BUTTON_* id；未知返回 -1。 */
    public static int buttonId(String name)
    {
        Integer id = BUTTON_IDS.get(name);
        return id == null ? -1 : id;
    }
}
