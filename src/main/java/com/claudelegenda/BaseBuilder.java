package com.claudelegenda;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.block.enums.PistonType;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;

/**
 * Секретная база Клода. Строится ОДИН раз, когда игрок впервые подходит к координатам входа
 * (до этого на карте нет ничего). Высота поверхности запоминается в файле мира
 * claude_legenda_base.txt, чтобы после перезапуска сервера всё совпадало.
 *
 * Схема (вид сверху, вход слева сверху, ось Z вниз):
 *   поверхность (ex, ez) -> лестница вниз на n блоков -> прихожая 3x2 (поршень в полу)
 *   -> стена-дверь -> затопленная комната 17x11x17 со статуей, табличкой и сундуками.
 */
public final class BaseBuilder {
    private BaseBuilder() {}

    // ------------------------------------------------------------- геометрия

    record Layout(int ex, int ez, int sy, int oy, int n) {
        static Layout of(int sy, int bottomY) {
            int oy = Math.max(sy - ClaudeConfig.DEPTH, bottomY + 10);
            oy = Math.min(oy, sy - 6);
            return new Layout(ClaudeConfig.ENTRANCE_X, ClaudeConfig.ENTRANCE_Z, sy, oy, sy - oy);
        }

        int rz0() { return ez + n + 3; }   // первый z внутри комнаты
        int rz1() { return rz0() + 16; }   // последний z внутри комнаты
        int zc() { return rz0() + 8; }     // центр комнаты по Z

        /** Скрытый поршень: в полу последней ступеньки, смотрит на юг, в сторону комнаты. */
        BlockPos piston() { return new BlockPos(ex, oy, ez + n - 1); }

        boolean inTrigger(BlockPos p) {
            return p.getZ() == ez + n && Math.abs(p.getX() - ex) <= 1
                    && p.getY() >= oy && p.getY() <= oy + 1;
        }

        boolean inRoom(BlockPos p) {
            return Math.abs(p.getX() - ex) <= 8 && p.getY() >= oy && p.getY() <= oy + 10
                    && p.getZ() >= rz0() && p.getZ() <= rz1();
        }
    }

    private static Layout layout;
    private static final Map<UUID, Integer> PENDING = new HashMap<>();

    // Материалы
    private static final BlockState SHELL = Blocks.POLISHED_DEEPSLATE.getDefaultState();
    private static final BlockState TUNNEL = Blocks.STONE_BRICKS.getDefaultState();
    private static final BlockState AIR = Blocks.AIR.getDefaultState();
    private static final BlockState WATER = Blocks.WATER.getDefaultState();
    private static final BlockState LANTERN = Blocks.SEA_LANTERN.getDefaultState();
    private static final BlockState BODY = Blocks.ORANGE_CONCRETE.getDefaultState();
    private static final BlockState BLACK = Blocks.BLACK_CONCRETE.getDefaultState();

    // ---------------------------------------------------- жизненный цикл мира

    private static Path file(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("claude_legenda_base.txt");
    }

    static void onServerStarted(MinecraftServer server) {
        layout = null;
        PENDING.clear();
        Path f = file(server);
        if (Files.exists(f)) {
            try {
                int sy = Integer.parseInt(Files.readString(f).trim());
                layout = Layout.of(sy, server.getOverworld().getBottomY());
            } catch (IOException | NumberFormatException e) {
                // файл повреждён — база будет построена заново при приближении
            }
        }
    }

    static void onServerStopped() {
        layout = null;
        PENDING.clear();
    }

    // -------------------------------------------------------------- игровой тик

    static void tickPlayer(MinecraftServer server, ServerWorld world, ServerPlayerEntity p, int tick) {
        if (layout == null) {
            if (tick % 20 != 0) {
                return;
            }
            double dx = p.getX() - ClaudeConfig.ENTRANCE_X;
            double dz = p.getZ() - ClaudeConfig.ENTRANCE_Z;
            double r = ClaudeConfig.BUILD_RADIUS;
            if (dx * dx + dz * dz <= r * r) {
                build(server, world);
            }
            return;
        }
        BlockPos bp = p.getBlockPos();
        if (!StoryManager.has(p, StoryManager.F_TRIGGERED) && layout.inTrigger(bp)) {
            fire(p, world);
        }
        if (!StoryManager.has(p, StoryManager.F_BASE) && layout.inRoom(bp)) {
            StoryManager.onArrive(p);
        }
    }

    /** Страховка: если поршневой толчок не донёс игрока, телепортируем его в комнату. */
    static void tickPending(MinecraftServer server) {
        if (PENDING.isEmpty() || layout == null) {
            return;
        }
        Iterator<Map.Entry<UUID, Integer>> it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> e = it.next();
            int left = e.getValue() - 1;
            if (left > 0) {
                e.setValue(left);
                continue;
            }
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(e.getKey());
            if (p != null && !layout.inRoom(p.getBlockPos())) {
                p.requestTeleport(layout.ex() + 0.5, layout.oy() + 1.0, layout.rz0() + 3.5);
            }
            it.remove();
        }
    }

    // ------------------------------------------------------------ ловушка

    private static void fire(ServerPlayerEntity p, ServerWorld w) {
        Layout L = layout;
        StoryManager.set(p, StoryManager.F_TRIGGERED, true);

        // 1) «дверь» в стене комнаты исчезает, вода хлынет в прихожую
        box(w, L.ex() - 1, L.oy(), L.rz0() - 1, L.ex() + 1, L.oy() + 3, L.rz0() - 1, AIR);

        // 2) поршень выдвигается на юг
        BlockPos pp = L.piston();
        w.setBlockState(pp, Blocks.PISTON.getDefaultState()
                .with(Properties.FACING, Direction.SOUTH)
                .with(Properties.EXTENDED, true), Block.NOTIFY_LISTENERS);
        w.setBlockState(pp.south(), Blocks.PISTON_HEAD.getDefaultState()
                .with(Properties.FACING, Direction.SOUTH)
                .with(Properties.PISTON_TYPE, PistonType.DEFAULT), Block.NOTIFY_LISTENERS);
        w.playSound(null, pp, SoundEvents.BLOCK_PISTON_EXTEND, SoundCategory.BLOCKS, 1.5f, 0.7f);

        // 3) резкий толчок вперёд, в комнату
        p.setVelocity(0.0, 0.2, 1.4);
        p.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(p));
        PENDING.put(p.getUuid(), 15);
    }

    // ------------------------------------------------------------ строительство

    private static void build(MinecraftServer server, ServerWorld w) {
        int ex = ClaudeConfig.ENTRANCE_X;
        int ez = ClaudeConfig.ENTRANCE_Z;

        // Прогружаем нужные чанки (одноразовая подвисалка).
        for (int cx = (ex - 16) >> 4; cx <= (ex + 16) >> 4; cx++) {
            for (int cz = (ez - 4) >> 4; cz <= (ez + 44) >> 4; cz++) {
                w.getChunk(cx, cz);
            }
        }

        int sy = w.getTopY(Heightmap.Type.OCEAN_FLOOR, ex, ez);
        Layout L = Layout.of(sy, w.getBottomY());
        int oy = L.oy();
        int n = L.n();
        int rz0 = L.rz0();
        int rz1 = L.rz1();

        // --- комната: оболочка, потом вода внутри
        box(w, ex - 9, oy - 1, rz0 - 1, ex + 9, oy + 11, rz1 + 1, SHELL);
        box(w, ex - 8, oy, rz0, ex + 8, oy + 10, rz1, WATER);
        for (int x : new int[] {ex - 6, ex - 2, ex + 2, ex + 6}) {
            for (int z : new int[] {rz0 + 2, rz0 + 6, rz0 + 10, rz0 + 14}) {
                set(w, x, oy + 11, z, LANTERN);
            }
        }
        for (int x : new int[] {ex - 6, ex + 6}) {
            for (int z : new int[] {rz0 + 2, rz1 - 2}) {
                set(w, x, oy - 1, z, LANTERN);
            }
        }

        // --- прихожая 3x2 (сухая)
        box(w, ex - 2, oy - 1, ez + n, ex + 2, oy + 4, ez + n + 1, SHELL);
        box(w, ex - 1, oy, ez + n, ex + 1, oy + 3, ez + n + 1, AIR);

        // --- лестница с поверхности
        for (int i = 0; i < n; i++) {
            int y = sy - i;
            int z = ez + i;
            for (int dx = -1; dx <= 1; dx++) {
                set(w, ex + dx, y - 1, z, TUNNEL); // пол
                for (int dy = 0; dy <= 3; dy++) {
                    set(w, ex + dx, y + dy, z, AIR);
                }
                wall(w, ex + dx, y + 4, z, sy); // потолок
            }
            for (int dy = -1; dy <= 3; dy++) {
                wall(w, ex - 2, y + dy, z, sy);
                wall(w, ex + 2, y + dy, z, sy);
            }
        }

        // --- скрытый поршень (пока втянут), в полу последней ступеньки
        BlockPos pp = L.piston();
        w.setBlockState(pp, Blocks.PISTON.getDefaultState().with(Properties.FACING, Direction.SOUTH),
                Block.NOTIFY_LISTENERS);

        // --- наполнение
        buildStatue(w, L);
        buildSign(w, L);
        buildChests(w, L);

        // --- запоминаем
        layout = L;
        try {
            Files.writeString(file(server), Integer.toString(sy));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void buildStatue(ServerWorld w, Layout L) {
        int cx = L.ex();
        int cz = L.zc();
        int oy = L.oy();

        // постамент
        box(w, cx - 3, oy, cz - 3, cx + 3, oy, cz + 3, Blocks.SMOOTH_QUARTZ.getDefaultState());
        // ноги, торс, голова
        box(w, cx - 2, oy + 1, cz - 1, cx - 1, oy + 3, cz + 1, BODY);
        box(w, cx + 1, oy + 1, cz - 1, cx + 2, oy + 3, cz + 1, BODY);
        box(w, cx - 2, oy + 4, cz - 1, cx + 2, oy + 6, cz + 1, BODY);
        box(w, cx - 2, oy + 7, cz - 1, cx + 2, oy + 9, cz + 1, BODY);
        // руки вверх «ёлочкой»
        for (int k = 0; k < 3; k++) {
            set(w, cx - 3 - k, oy + 6 + k, cz, BODY);
            set(w, cx + 3 + k, oy + 6 + k, cz, BODY);
        }
        // лицо (смотрит на север, к входу)
        set(w, cx - 1, oy + 8, cz - 1, BLACK);
        set(w, cx + 1, oy + 8, cz - 1, BLACK);
        box(w, cx - 1, oy + 7, cz - 1, cx + 1, oy + 7, cz - 1, BLACK);
        // сияние над головой
        set(w, cx, oy + 10, cz, Blocks.GLOWSTONE.getDefaultState());
    }

    private static void buildSign(ServerWorld w, Layout L) {
        BlockPos sp = new BlockPos(L.ex(), L.oy(), L.zc() - 5);
        BlockState state = Blocks.OAK_SIGN.getDefaultState()
                .with(Properties.ROTATION, 8) // 8 = лицом на север, к входу
                .with(Properties.WATERLOGGED, true);
        w.setBlockState(sp, state, Block.NOTIFY_LISTENERS);
        if (w.getBlockEntity(sp) instanceof SignBlockEntity sign) {
            SignText text = new SignText()
                    .withMessage(1, Text.literal("Клод — легенда"))
                    .withColor(DyeColor.YELLOW)
                    .withGlowing(true);
            sign.setText(text, true);
            sign.setWaxed(true);
            sign.markDirty();
            w.updateListeners(sp, state, state, 3);
        }
    }

    private static void buildChests(ServerWorld w, Layout L) {
        Registry<Enchantment> reg = w.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
        int z = L.zc() + 5;
        int y = L.oy();
        int cx = L.ex();

        // Сундук 1: броня и инструменты с имбовыми чарами
        chest(w, new BlockPos(cx - 4, y, z),
                gear(reg, Items.NETHERITE_HELMET,
                        e(Enchantments.PROTECTION, 5), e(Enchantments.UNBREAKING, 5), e(Enchantments.MENDING, 1),
                        e(Enchantments.RESPIRATION, 3), e(Enchantments.AQUA_AFFINITY, 1), e(Enchantments.THORNS, 3)),
                gear(reg, Items.NETHERITE_CHESTPLATE,
                        e(Enchantments.PROTECTION, 5), e(Enchantments.UNBREAKING, 5), e(Enchantments.MENDING, 1),
                        e(Enchantments.THORNS, 3)),
                gear(reg, Items.NETHERITE_LEGGINGS,
                        e(Enchantments.PROTECTION, 5), e(Enchantments.UNBREAKING, 5), e(Enchantments.MENDING, 1),
                        e(Enchantments.SWIFT_SNEAK, 5)),
                gear(reg, Items.NETHERITE_BOOTS,
                        e(Enchantments.PROTECTION, 5), e(Enchantments.UNBREAKING, 5), e(Enchantments.MENDING, 1),
                        e(Enchantments.FEATHER_FALLING, 5), e(Enchantments.DEPTH_STRIDER, 4),
                        e(Enchantments.SOUL_SPEED, 4)),
                gear(reg, Items.NETHERITE_SWORD,
                        e(Enchantments.SHARPNESS, 7), e(Enchantments.SWEEPING_EDGE, 5), e(Enchantments.LOOTING, 5),
                        e(Enchantments.FIRE_ASPECT, 3), e(Enchantments.UNBREAKING, 5), e(Enchantments.MENDING, 1)),
                gear(reg, Items.NETHERITE_PICKAXE,
                        e(Enchantments.EFFICIENCY, 8), e(Enchantments.FORTUNE, 5), e(Enchantments.UNBREAKING, 5),
                        e(Enchantments.MENDING, 1)),
                gear(reg, Items.NETHERITE_SHOVEL,
                        e(Enchantments.EFFICIENCY, 8), e(Enchantments.FORTUNE, 5), e(Enchantments.UNBREAKING, 5),
                        e(Enchantments.MENDING, 1)),
                gear(reg, Items.NETHERITE_HOE,
                        e(Enchantments.EFFICIENCY, 8), e(Enchantments.FORTUNE, 5), e(Enchantments.UNBREAKING, 5),
                        e(Enchantments.MENDING, 1)));

        // Сундук 2: алмазы, шаблоны «Тишина», опыт, 3 стака зачарованных яблок, 3 стака золотой моркови, 10 тотемов
        chest(w, new BlockPos(cx, y, z),
                new ItemStack(Items.DIAMOND, 64),
                new ItemStack(Items.DIAMOND, 64),
                new ItemStack(Items.DIAMOND, 64),
                new ItemStack(Items.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE, 64),
                new ItemStack(Items.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE, 64),
                new ItemStack(Items.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE, 64),
                new ItemStack(Items.EXPERIENCE_BOTTLE, 64),
                new ItemStack(Items.EXPERIENCE_BOTTLE, 6),
                new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 64),
                new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 64),
                new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 64),
                new ItemStack(Items.GOLDEN_CARROT, 64),
                new ItemStack(Items.GOLDEN_CARROT, 64),
                new ItemStack(Items.GOLDEN_CARROT, 64),
                new ItemStack(Items.TOTEM_OF_UNDYING, 1),
                new ItemStack(Items.TOTEM_OF_UNDYING, 1),
                new ItemStack(Items.TOTEM_OF_UNDYING, 1),
                new ItemStack(Items.TOTEM_OF_UNDYING, 1),
                new ItemStack(Items.TOTEM_OF_UNDYING, 1),
                new ItemStack(Items.TOTEM_OF_UNDYING, 1),
                new ItemStack(Items.TOTEM_OF_UNDYING, 1),
                new ItemStack(Items.TOTEM_OF_UNDYING, 1),
                new ItemStack(Items.TOTEM_OF_UNDYING, 1),
                new ItemStack(Items.TOTEM_OF_UNDYING, 1));

        // Сундук 3: столы, материалы и финальная книга
        chest(w, new BlockPos(cx + 4, y, z),
                new ItemStack(Items.SMITHING_TABLE, 1),
                new ItemStack(Items.ENCHANTING_TABLE, 1),
                new ItemStack(Items.CRAFTING_TABLE, 1),
                new ItemStack(Items.OAK_PLANKS, 64),
                new ItemStack(Items.STICK, 64),
                new ItemStack(Items.GOLD_INGOT, 3),
                new ItemStack(Items.IRON_INGOT, 1),
                gear(reg, Items.NETHERITE_AXE,
                        e(Enchantments.EFFICIENCY, 8), e(Enchantments.SHARPNESS, 7), e(Enchantments.UNBREAKING, 5),
                        e(Enchantments.MENDING, 1)),
                new ItemStack(Items.COOKED_BEEF, 64),
                new ItemStack(Items.COOKED_BEEF, 64),
                Books.thanks());
    }

    // --------------------------------------------------------------- помощники

    private record Ench(RegistryKey<Enchantment> key, int level) {}

    private static Ench e(RegistryKey<Enchantment> key, int level) {
        return new Ench(key, level);
    }

    private static ItemStack gear(Registry<Enchantment> reg, Item item, Ench... enchants) {
        ItemStack stack = new ItemStack(item);
        for (Ench en : enchants) {
            stack.addEnchantment(reg.getOrThrow(en.key()), en.level());
        }
        return stack;
    }

    private static void chest(ServerWorld w, BlockPos pos, ItemStack... items) {
        w.setBlockState(pos, Blocks.CHEST.getDefaultState()
                .with(Properties.HORIZONTAL_FACING, Direction.NORTH)
                .with(Properties.WATERLOGGED, true), Block.NOTIFY_LISTENERS);
        if (w.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
            for (int i = 0; i < items.length; i++) {
                chest.setStack(i, items[i]);
            }
            chest.markDirty();
        }
    }

    /** Облицовка стенок лестницы — только под землёй и только там, где не твёрдо. */
    private static void wall(ServerWorld w, int x, int y, int z, int surfaceY) {
        if (y > surfaceY - 1) {
            return;
        }
        BlockPos p = new BlockPos(x, y, z);
        if (!w.getBlockState(p).isSolidBlock(w, p)) {
            w.setBlockState(p, TUNNEL, Block.NOTIFY_LISTENERS);
        }
    }

    private static void set(ServerWorld w, int x, int y, int z, BlockState s) {
        w.setBlockState(new BlockPos(x, y, z), s, Block.NOTIFY_LISTENERS);
    }

    private static void box(ServerWorld w, int x1, int y1, int z1, int x2, int y2, int z2, BlockState s) {
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    w.setBlockState(m.set(x, y, z), s, Block.NOTIFY_LISTENERS);
                }
            }
        }
    }
}
