package net.montoyo.wd.utilities.data;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.EnumProperty;

public enum ScreenPieceType implements StringRepresentable {
    FULL("full"),
    HALF_BOTTOM("half_bottom"),
    HALF_TOP("half_top"),
    HALF_LEFT("half_left"),
    HALF_RIGHT("half_right"),
    TRIANGLE_SW("triangle_sw"),
    TRIANGLE_SE("triangle_se"),
    TRIANGLE_NW("triangle_nw"),
    TRIANGLE_NE("triangle_ne");

    public static final EnumProperty<ScreenPieceType> PROPERTY = EnumProperty.create("piece", ScreenPieceType.class);

    private final String name;

    ScreenPieceType(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public boolean isFull() {
        return this == FULL;
    }

    public boolean isHalf() {
        return this == HALF_BOTTOM || this == HALF_TOP || this == HALF_LEFT || this == HALF_RIGHT;
    }

    public boolean isTriangle() {
        return this == TRIANGLE_SW || this == TRIANGLE_SE || this == TRIANGLE_NW || this == TRIANGLE_NE;
    }

    /**
     * Tests whether local coordinates inside a screen cell belong to the visible area.
     * fx/fy are in [0, 1], with x to the right and y upward on the screen plane.
     */
    public boolean contains(float fx, float fy) {
        return switch (this) {
            case FULL -> true;
            case HALF_BOTTOM -> fy <= 0.5f;
            case HALF_TOP -> fy >= 0.5f;
            case HALF_LEFT -> fx <= 0.5f;
            case HALF_RIGHT -> fx >= 0.5f;
            case TRIANGLE_SW -> fx + fy <= 1.0f;
            case TRIANGLE_SE -> (1.0f - fx) + fy <= 1.0f;
            case TRIANGLE_NW -> fx + (1.0f - fy) <= 1.0f;
            case TRIANGLE_NE -> fx + fy >= 1.0f;
        };
    }

    public static ScreenPieceType pickHalfFromYaw(float yaw) {
        int q = (int) Math.floor((yaw + 180.0f) / 90.0f) & 3;
        return switch (q) {
            case 0 -> HALF_BOTTOM;
            case 1 -> HALF_LEFT;
            case 2 -> HALF_TOP;
            default -> HALF_RIGHT;
        };
    }

    public static ScreenPieceType pickTriangleFromYaw(float yaw) {
        int q = (int) Math.floor((yaw + 180.0f) / 90.0f) & 3;
        return switch (q) {
            case 0 -> TRIANGLE_SW;
            case 1 -> TRIANGLE_NW;
            case 2 -> TRIANGLE_NE;
            default -> TRIANGLE_SE;
        };
    }
}
