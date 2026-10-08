package com.mesen;

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
}
