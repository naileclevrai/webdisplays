package net.montoyo.wd.utilities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.montoyo.wd.block.ScreenBlock;
import net.montoyo.wd.registry.BlockRegistry;
import net.montoyo.wd.utilities.data.BlockSide;
import net.montoyo.wd.utilities.data.ScreenShapeMode;
import net.montoyo.wd.utilities.math.Vector2i;
import net.montoyo.wd.utilities.math.Vector3i;

public final class ScreenShape {
    public static final byte SMOOTH_NONE = 0;
    public static final byte SMOOTH_BOTTOM_LEFT = 1;
    public static final byte SMOOTH_TOP_LEFT = 2;
    public static final byte SMOOTH_BOTTOM_RIGHT = 3;
    public static final byte SMOOTH_TOP_RIGHT = 4;

    private ScreenShape() {
    }

    private static final class Endpoint {
        final int x;
        final int y;
        final int dx;
        final int dy;

        private Endpoint(int x, int y, int dx, int dy) {
            this.x = x;
            this.y = y;
            this.dx = dx;
            this.dy = dy;
        }
    }

    private static final class Arc {
        final float cx;
        final float cy;
        final float r;
        final float start;
        final float end;
        final boolean ccw;
        final boolean useX;

        private Arc(float cx, float cy, float r, float start, float end, boolean ccw, boolean useX) {
            this.cx = cx;
            this.cy = cy;
            this.r = r;
            this.start = start;
            this.end = end;
            this.ccw = ccw;
            this.useX = useX;
        }
    }

    public static final class Bounds {
        public final int minX;
        public final int minY;
        public final int maxX;
        public final int maxY;

        private Bounds(int minX, int minY, int maxX, int maxY) {
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
        }

        public Vector2i size() {
            return new Vector2i(maxX - minX + 1, maxY - minY + 1);
        }
    }

    public static final class Data {
        public final boolean[] mask;
        public final int width;
        public final int height;
        public final int[] columnHeights;
        public final int[] columnBottoms;
        public final int[] rowLefts;
        public final int[] rowRights;
        public final float[] boundary;
        public final float[] topLeft;
        public final float[] topRight;
        public final float[] bottomLeft;
        public final float[] bottomRight;
        public final float[] leftBottom;
        public final float[] leftTop;
        public final float[] rightBottom;
        public final float[] rightTop;
        public final byte[] smoothMask;

        private Data(boolean[] mask, int width, int height, int[] columnHeights, int[] columnBottoms, int[] rowLefts, int[] rowRights,
                     float[] boundary, float[] topLeft, float[] topRight, float[] bottomLeft, float[] bottomRight,
                     float[] leftBottom, float[] leftTop, float[] rightBottom, float[] rightTop, byte[] smoothMask) {
            this.mask = mask;
            this.width = width;
            this.height = height;
            this.columnHeights = columnHeights;
            this.columnBottoms = columnBottoms;
            this.rowLefts = rowLefts;
            this.rowRights = rowRights;
            this.boundary = boundary;
            this.topLeft = topLeft;
            this.topRight = topRight;
            this.bottomLeft = bottomLeft;
            this.bottomRight = bottomRight;
            this.leftBottom = leftBottom;
            this.leftTop = leftTop;
            this.rightBottom = rightBottom;
            this.rightTop = rightTop;
            this.smoothMask = smoothMask;
        }

        public boolean hasBlock(int x, int y) {
            return mask[y * width + x];
        }
    }

    public static Data compute(BlockGetter level, BlockPos origin, BlockSide side, Vector2i size, ScreenShapeMode mode) {
        return compute(level, origin, side, size, mode, null);
    }

    public static Data compute(BlockGetter level, BlockPos origin, BlockSide side, Vector2i size, ScreenShapeMode mode, BlockPos seed) {
        int width = size.x;
        int height = size.y;
        boolean[] raw = new boolean[width * height];
        boolean[] mask = new boolean[width * height];
        int[] heights = new int[width];
        int[] bottoms = new int[width];
        int[] rowLefts = new int[height];
        int[] rowRights = new int[height];
        Vector3i pos = new Vector3i(origin);
        BlockPos.MutableBlockPos bp = new BlockPos.MutableBlockPos();

        for (int x = 0; x < width; x++)
            bottoms[x] = Integer.MAX_VALUE;
        for (int y = 0; y < height; y++) {
            rowLefts[y] = Integer.MAX_VALUE;
            rowRights[y] = Integer.MIN_VALUE;
        }

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                pos.toBlock(bp);
                boolean isScreen = level.getBlockState(bp).getBlock() == BlockRegistry.SCREEN_BLOCk.get();
                raw[y * width + x] = isScreen;
                pos.add(side.right);
            }

            pos.addMul(side.left, width);
            pos.add(side.up);
        }

        int startIdx = -1;
        if (seed != null && width > 0 && height > 0) {
            int dx = seed.getX() - origin.getX();
            int dy = seed.getY() - origin.getY();
            int dz = seed.getZ() - origin.getZ();
            int sx = dx * side.right.x + dy * side.right.y + dz * side.right.z;
            int sy = dx * side.up.x + dy * side.up.y + dz * side.up.z;
            if (sx >= 0 && sx < width && sy >= 0 && sy < height) {
                int idx = sy * width + sx;
                if (raw[idx])
                    startIdx = idx;
            }
        }

        if (startIdx == -1 && width > 0 && height > 0 && raw[0])
            startIdx = 0;

        if (startIdx == -1) {
            for (int i = 0; i < raw.length; i++) {
                if (raw[i]) {
                    startIdx = i;
                    break;
                }
            }
        }

        if (width > 0 && height > 0 && startIdx >= 0) {
            int[] queue = new int[width * height];
            int head = 0;
            int tail = 0;
            queue[tail++] = startIdx;
            mask[startIdx] = true;

            while (head < tail) {
                int idx = queue[head++];
                int x = idx % width;
                int y = idx / width;

                if (x > 0) {
                    int left = idx - 1;
                    if (raw[left] && !mask[left]) {
                        mask[left] = true;
                        queue[tail++] = left;
                    }
                }
                if (x + 1 < width) {
                    int right = idx + 1;
                    if (raw[right] && !mask[right]) {
                        mask[right] = true;
                        queue[tail++] = right;
                    }
                }
                if (y > 0) {
                    int down = idx - width;
                    if (raw[down] && !mask[down]) {
                        mask[down] = true;
                        queue[tail++] = down;
                    }
                }
                if (y + 1 < height) {
                    int up = idx + width;
                    if (raw[up] && !mask[up]) {
                        mask[up] = true;
                        queue[tail++] = up;
                    }
                }
                if (x > 0 && y > 0) {
                    int downLeft = idx - width - 1;
                    if (raw[downLeft] && !mask[downLeft]) {
                        mask[downLeft] = true;
                        queue[tail++] = downLeft;
                    }
                }
                if (x + 1 < width && y > 0) {
                    int downRight = idx - width + 1;
                    if (raw[downRight] && !mask[downRight]) {
                        mask[downRight] = true;
                        queue[tail++] = downRight;
                    }
                }
                if (x > 0 && y + 1 < height) {
                    int upLeft = idx + width - 1;
                    if (raw[upLeft] && !mask[upLeft]) {
                        mask[upLeft] = true;
                        queue[tail++] = upLeft;
                    }
                }
                if (x + 1 < width && y + 1 < height) {
                    int upRight = idx + width + 1;
                    if (raw[upRight] && !mask[upRight]) {
                        mask[upRight] = true;
                        queue[tail++] = upRight;
                    }
                }
            }
        }

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (mask[y * width + x]) {
                    heights[x] = Math.max(heights[x], y + 1);
                    rowLefts[y] = Math.min(rowLefts[y], x);
                    rowRights[y] = Math.max(rowRights[y], x + 1);
                }
            }
        }
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (mask[y * width + x])
                    bottoms[x] = Math.min(bottoms[x], y);
            }
        }
        for (int x = 0; x < width; x++) {
            if (bottoms[x] == Integer.MAX_VALUE)
                bottoms[x] = -1;
        }
        for (int y = 0; y < height; y++) {
            if (rowLefts[y] == Integer.MAX_VALUE) {
                rowLefts[y] = -1;
                rowRights[y] = -1;
            }
        }

        float[] boundary = null;
        float[] topLeft = null;
        float[] topRight = null;
        float[] bottomLeft = null;
        float[] bottomRight = null;
        float[] leftBottom = null;
        float[] leftTop = null;
        float[] rightBottom = null;
        float[] rightTop = null;
        byte[] smoothMask = null;
        if (mode != null && mode != ScreenShapeMode.NONE) {
            if (mode == ScreenShapeMode.SMOOTH_LONG) {
                boundary = buildBoundarySmoothLong(heights);
                float[] reverseBoundary = buildBoundarySmoothLongReverse(heights);
                topLeft = new float[width];
                topRight = new float[width];
                for (int x = 0; x < width; x++) {
                    float colHeight = heights[x];
                    topLeft[x] = Math.max(colHeight, reverseBoundary[x]);
                    topRight[x] = Math.max(colHeight, boundary[x + 1]);
                }

                float[] bottomBoundary = buildBoundarySmoothLongBottom(bottoms);
                float[] reverseBottomBoundary = buildBoundarySmoothLongBottomReverse(bottoms);
                bottomLeft = new float[width];
                bottomRight = new float[width];
                for (int x = 0; x < width; x++) {
                    float colBottom = bottoms[x];
                    if (colBottom < 0) {
                        bottomLeft[x] = colBottom;
                        bottomRight[x] = colBottom;
                    } else {
                        bottomLeft[x] = Math.min(colBottom, reverseBottomBoundary[x]);
                        bottomRight[x] = Math.min(colBottom, bottomBoundary[x + 1]);
                    }
                }

                float[] leftBoundary = buildBoundarySmoothLongBottom(rowLefts);
                float[] reverseLeftBoundary = buildBoundarySmoothLongBottomReverse(rowLefts);
                leftBottom = new float[height];
                leftTop = new float[height];
                for (int y = 0; y < height; y++) {
                    float rowLeft = rowLefts[y];
                    if (rowLeft < 0) {
                        leftBottom[y] = rowLeft;
                        leftTop[y] = rowLeft;
                    } else {
                        leftBottom[y] = Math.min(rowLeft, reverseLeftBoundary[y]);
                        leftTop[y] = Math.min(rowLeft, leftBoundary[y + 1]);
                    }
                }

                float[] rightBoundary = buildBoundarySmoothLong(rowRights);
                float[] reverseRightBoundary = buildBoundarySmoothLongReverse(rowRights);
                rightBottom = new float[height];
                rightTop = new float[height];
                for (int y = 0; y < height; y++) {
                    float rowRight = rowRights[y];
                    if (rowRight < 0) {
                        rightBottom[y] = rowRight;
                        rightTop[y] = rowRight;
                    } else {
                        rightBottom[y] = Math.max(rowRight, reverseRightBoundary[y]);
                        rightTop[y] = Math.max(rowRight, rightBoundary[y + 1]);
                    }
                }
            } else {
                topLeft = new float[width];
                topRight = new float[width];
                for (int x = 0; x < width; x++) {
					float base = heights[x];
					float leftHeight = base;
					float rightHeight = base;
					boolean left = x > 0 && heights[x - 1] > heights[x];
					boolean right = x + 1 < width && heights[x + 1] > heights[x];
					if (left && heights[x] > 0) {
						int y = heights[x] - 1;
						left = y >= 0 && y < height && mask[y * width + (x - 1)];
					}
					if (right && heights[x] > 0) {
						int y = heights[x] - 1;
						right = y >= 0 && y < height && mask[y * width + (x + 1)];
					}
					if (left)
						leftHeight = Math.min(heights[x] + 1, heights[x - 1]);
					if (right)
						rightHeight = Math.min(heights[x] + 1, heights[x + 1]);
					topLeft[x] = leftHeight;
                    topRight[x] = rightHeight;
                }

                bottomLeft = new float[width];
                bottomRight = new float[width];
                for (int x = 0; x < width; x++) {
                    int base = bottoms[x];
                    if (base < 0) {
                        bottomLeft[x] = base;
                        bottomRight[x] = base;
                        continue;
                    }

					int leftHeight = base;
					int rightHeight = base;
					boolean left = x > 0 && bottoms[x - 1] >= 0 && bottoms[x - 1] < bottoms[x];
					boolean right = x + 1 < width && bottoms[x + 1] >= 0 && bottoms[x + 1] < bottoms[x];
					if (left) {
						int y = bottoms[x];
						left = y >= 0 && y < height && mask[y * width + (x - 1)];
					}
					if (right) {
						int y = bottoms[x];
						right = y >= 0 && y < height && mask[y * width + (x + 1)];
					}
					if (left)
						leftHeight = Math.max(bottoms[x] - 1, bottoms[x - 1]);
					if (right)
						rightHeight = Math.max(bottoms[x] - 1, bottoms[x + 1]);
					bottomLeft[x] = leftHeight;
                    bottomRight[x] = rightHeight;
                }

                leftBottom = new float[height];
                leftTop = new float[height];
                rightBottom = new float[height];
                rightTop = new float[height];
                for (int y = 0; y < height; y++) {
                    int rowLeft = rowLefts[y];
                    if (rowLeft < 0) {
                        leftBottom[y] = rowLeft;
                        leftTop[y] = rowLeft;
                    } else {
						int down = rowLeft;
						int up = rowLeft;
						boolean downAdj = y > 0 && rowLefts[y - 1] >= 0 && rowLefts[y - 1] < rowLeft;
						boolean upAdj = y + 1 < height && rowLefts[y + 1] >= 0 && rowLefts[y + 1] < rowLeft;
						if (downAdj && rowLeft >= 0 && rowLeft < width)
							downAdj = mask[(y - 1) * width + rowLeft];
						if (upAdj && rowLeft >= 0 && rowLeft < width)
							upAdj = mask[(y + 1) * width + rowLeft];
						if (downAdj)
							down = Math.max(rowLeft - 1, rowLefts[y - 1]);
						if (upAdj)
							up = Math.max(rowLeft - 1, rowLefts[y + 1]);
                        leftBottom[y] = down;
                        leftTop[y] = up;
                    }

                    int rowRight = rowRights[y];
                    if (rowRight < 0) {
                        rightBottom[y] = rowRight;
                        rightTop[y] = rowRight;
                    } else {
						int down = rowRight;
						int up = rowRight;
						boolean downAdj = y > 0 && rowRights[y - 1] >= 0 && rowRights[y - 1] > rowRight;
						boolean upAdj = y + 1 < height && rowRights[y + 1] >= 0 && rowRights[y + 1] > rowRight;
						int col = rowRight - 1;
						if (col >= 0 && col < width) {
							if (downAdj)
								downAdj = mask[(y - 1) * width + col];
							if (upAdj)
								upAdj = mask[(y + 1) * width + col];
						} else {
							downAdj = false;
							upAdj = false;
						}
						if (downAdj)
							down = Math.min(rowRight + 1, rowRights[y - 1]);
						if (upAdj)
							up = Math.min(rowRight + 1, rowRights[y + 1]);
                        rightBottom[y] = down;
                        rightTop[y] = up;
                    }
                }
                smoothMask = new byte[width * height];
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int idx = y * width + x;
                        if (mask[idx])
                            continue;
                        boolean left = x > 0 && mask[y * width + (x - 1)];
                        boolean right = x + 1 < width && mask[y * width + (x + 1)];
                        boolean down = y > 0 && mask[(y - 1) * width + x];
                        boolean up = y + 1 < height && mask[(y + 1) * width + x];

                        if (left && down && !right && !up) {
                            smoothMask[idx] = SMOOTH_BOTTOM_LEFT;
                        } else if (left && up && !right && !down) {
                            smoothMask[idx] = SMOOTH_TOP_LEFT;
                        } else if (right && down && !left && !up) {
                            smoothMask[idx] = SMOOTH_BOTTOM_RIGHT;
                        } else if (right && up && !left && !down) {
                            smoothMask[idx] = SMOOTH_TOP_RIGHT;
                        }
                    }
                }
            }
        }

        return new Data(mask, width, height, heights, bottoms, rowLefts, rowRights, boundary, topLeft, topRight, bottomLeft, bottomRight, leftBottom, leftTop, rightBottom, rightTop, smoothMask);
    }

    public static Vector2i computeBounds(BlockGetter level, BlockPos origin, BlockSide side, int maxWidth, int maxHeight) {
        if (maxWidth <= 0 || maxHeight <= 0)
            return null;

        BlockPos.MutableBlockPos bp = new BlockPos.MutableBlockPos();
        if (!isScreenAt(level, origin.getX(), origin.getY(), origin.getZ(), side, 0, 0, bp))
            return null;

        boolean[] visited = new boolean[maxWidth * maxHeight];
        int[] qx = new int[maxWidth * maxHeight];
        int[] qy = new int[maxWidth * maxHeight];
        int head = 0;
        int tail = 0;

        visited[0] = true;
        qx[tail] = 0;
        qy[tail] = 0;
        tail++;

        int maxX = 0;
        int maxY = 0;

        while (head < tail) {
            int x = qx[head];
            int y = qy[head];
            head++;

            if (x > maxX)
                maxX = x;
            if (y > maxY)
                maxY = y;

            if (x + 1 < maxWidth && tryEnqueue(level, origin, side, x + 1, y, maxWidth, visited, qx, qy, bp, tail))
                tail++;
            if (x - 1 >= 0 && tryEnqueue(level, origin, side, x - 1, y, maxWidth, visited, qx, qy, bp, tail))
                tail++;
            if (y + 1 < maxHeight && tryEnqueue(level, origin, side, x, y + 1, maxWidth, visited, qx, qy, bp, tail))
                tail++;
            if (y - 1 >= 0 && tryEnqueue(level, origin, side, x, y - 1, maxWidth, visited, qx, qy, bp, tail))
                tail++;
            if (x + 1 < maxWidth && y + 1 < maxHeight && tryEnqueue(level, origin, side, x + 1, y + 1, maxWidth, visited, qx, qy, bp, tail))
                tail++;
            if (x - 1 >= 0 && y + 1 < maxHeight && tryEnqueue(level, origin, side, x - 1, y + 1, maxWidth, visited, qx, qy, bp, tail))
                tail++;
            if (x + 1 < maxWidth && y - 1 >= 0 && tryEnqueue(level, origin, side, x + 1, y - 1, maxWidth, visited, qx, qy, bp, tail))
                tail++;
            if (x - 1 >= 0 && y - 1 >= 0 && tryEnqueue(level, origin, side, x - 1, y - 1, maxWidth, visited, qx, qy, bp, tail))
                tail++;
        }

        return new Vector2i(maxX + 1, maxY + 1);
    }

    public static Bounds computeBoundsFrom(BlockGetter level, BlockPos start, BlockSide side, int maxWidth, int maxHeight) {
        if (maxWidth <= 0 || maxHeight <= 0)
            return null;

        int rangeX = maxWidth * 2 - 1;
        int rangeY = maxHeight * 2 - 1;
        int originX = maxWidth - 1;
        int originY = maxHeight - 1;

        boolean[] visited = new boolean[rangeX * rangeY];
        int[] qx = new int[rangeX * rangeY];
        int[] qy = new int[rangeX * rangeY];
        BlockPos.MutableBlockPos bp = new BlockPos.MutableBlockPos();

        if (!isScreenAt(level, start.getX(), start.getY(), start.getZ(), side, 0, 0, bp))
            return null;

        int head = 0;
        int tail = 0;
        visited[originY * rangeX + originX] = true;
        qx[tail] = 0;
        qy[tail] = 0;
        tail++;

        int minX = 0;
        int minY = 0;
        int maxX = 0;
        int maxY = 0;

        while (head < tail) {
            int x = qx[head];
            int y = qy[head];
            head++;

            if (x < minX)
                minX = x;
            if (y < minY)
                minY = y;
            if (x > maxX)
                maxX = x;
            if (y > maxY)
                maxY = y;

            if (x + 1 < maxWidth && tryEnqueueRelative(level, start, side, x + 1, y, rangeX, originX, originY, visited, qx, qy, bp, tail))
                tail++;
            if (x - 1 > -maxWidth && tryEnqueueRelative(level, start, side, x - 1, y, rangeX, originX, originY, visited, qx, qy, bp, tail))
                tail++;
            if (y + 1 < maxHeight && tryEnqueueRelative(level, start, side, x, y + 1, rangeX, originX, originY, visited, qx, qy, bp, tail))
                tail++;
            if (y - 1 > -maxHeight && tryEnqueueRelative(level, start, side, x, y - 1, rangeX, originX, originY, visited, qx, qy, bp, tail))
                tail++;
            if (x + 1 < maxWidth && y + 1 < maxHeight && tryEnqueueRelative(level, start, side, x + 1, y + 1, rangeX, originX, originY, visited, qx, qy, bp, tail))
                tail++;
            if (x - 1 > -maxWidth && y + 1 < maxHeight && tryEnqueueRelative(level, start, side, x - 1, y + 1, rangeX, originX, originY, visited, qx, qy, bp, tail))
                tail++;
            if (x + 1 < maxWidth && y - 1 > -maxHeight && tryEnqueueRelative(level, start, side, x + 1, y - 1, rangeX, originX, originY, visited, qx, qy, bp, tail))
                tail++;
            if (x - 1 > -maxWidth && y - 1 > -maxHeight && tryEnqueueRelative(level, start, side, x - 1, y - 1, rangeX, originX, originY, visited, qx, qy, bp, tail))
                tail++;
        }

        return new Bounds(minX, minY, maxX, maxY);
    }

    public static Vector3i findConnectedOrigin(BlockGetter level, BlockPos start, BlockSide side, int maxWidth, int maxHeight) {
        Bounds bounds = computeBoundsFrom(level, start, side, maxWidth, maxHeight);
        if (bounds == null)
            return new Vector3i(start);

        Vector3i pos = new Vector3i(start);
        pos.addMul(side.right, bounds.minX);
        pos.addMul(side.up, bounds.minY);
        return pos;
    }

    public static Vector3i findConnectedScreenEntity(BlockGetter level, BlockPos start, BlockSide side, int maxWidth, int maxHeight) {
        if (maxWidth <= 0 || maxHeight <= 0)
            return null;

        int rangeX = maxWidth * 2 - 1;
        int rangeY = maxHeight * 2 - 1;
        int originX = maxWidth - 1;
        int originY = maxHeight - 1;

        boolean[] visited = new boolean[rangeX * rangeY];
        int[] qx = new int[rangeX * rangeY];
        int[] qy = new int[rangeX * rangeY];
        BlockPos.MutableBlockPos bp = new BlockPos.MutableBlockPos();

        if (!isScreenAt(level, start.getX(), start.getY(), start.getZ(), side, 0, 0, bp))
            return null;

        int head = 0;
        int tail = 0;
        visited[originY * rangeX + originX] = true;
        qx[tail] = 0;
        qy[tail] = 0;
        tail++;

        while (head < tail) {
            int x = qx[head];
            int y = qy[head];
            head++;

            bp.set(
                start.getX() + side.right.x * x + side.up.x * y,
                start.getY() + side.right.y * x + side.up.y * y,
                start.getZ() + side.right.z * x + side.up.z * y
            );
            if (level.getBlockState(bp).getBlock() == BlockRegistry.SCREEN_BLOCk.get()
                && level.getBlockState(bp).getValue(ScreenBlock.hasTE)) {
                return new Vector3i(bp);
            }

            if (x + 1 < maxWidth && tryEnqueueRelative(level, start, side, x + 1, y, rangeX, originX, originY, visited, qx, qy, bp, tail))
                tail++;
            if (x - 1 > -maxWidth && tryEnqueueRelative(level, start, side, x - 1, y, rangeX, originX, originY, visited, qx, qy, bp, tail))
                tail++;
            if (y + 1 < maxHeight && tryEnqueueRelative(level, start, side, x, y + 1, rangeX, originX, originY, visited, qx, qy, bp, tail))
                tail++;
            if (y - 1 > -maxHeight && tryEnqueueRelative(level, start, side, x, y - 1, rangeX, originX, originY, visited, qx, qy, bp, tail))
                tail++;
            if (x + 1 < maxWidth && y + 1 < maxHeight && tryEnqueueRelative(level, start, side, x + 1, y + 1, rangeX, originX, originY, visited, qx, qy, bp, tail))
                tail++;
            if (x - 1 > -maxWidth && y + 1 < maxHeight && tryEnqueueRelative(level, start, side, x - 1, y + 1, rangeX, originX, originY, visited, qx, qy, bp, tail))
                tail++;
            if (x + 1 < maxWidth && y - 1 > -maxHeight && tryEnqueueRelative(level, start, side, x + 1, y - 1, rangeX, originX, originY, visited, qx, qy, bp, tail))
                tail++;
            if (x - 1 > -maxWidth && y - 1 > -maxHeight && tryEnqueueRelative(level, start, side, x - 1, y - 1, rangeX, originX, originY, visited, qx, qy, bp, tail))
                tail++;
        }

        return null;
    }

    public static boolean isInside(Data data, ScreenShapeMode mode, float localX, float localY) {
        if (data == null)
            return false;

        if (localX < 0.0f || localY < 0.0f || localX >= data.width || localY >= data.height)
            return false;

        int x = (int) Math.floor(localX);
        int y = (int) Math.floor(localY);
        if (x < 0 || x >= data.width || y < 0 || y >= data.height)
            return false;

        if (data.hasBlock(x, y))
            return true;

        if (mode == ScreenShapeMode.SMOOTH_ONE && data.smoothMask != null) {
            byte tri = data.smoothMask[y * data.width + x];
            if (tri == SMOOTH_NONE)
                return false;

            float fx = localX - x;
            float fy = localY - y;
            switch (tri) {
                case SMOOTH_BOTTOM_LEFT:
                    return fy <= 1.0f - fx;
                case SMOOTH_TOP_LEFT:
                    return fy >= fx;
                case SMOOTH_BOTTOM_RIGHT:
                    return fy <= fx;
                case SMOOTH_TOP_RIGHT:
                    return fy >= 1.0f - fx;
                default:
                    return false;
            }
        }

        if (mode == null || mode == ScreenShapeMode.NONE || data.topLeft == null || data.topRight == null)
            return false;

        int colHeight = data.columnHeights[x];
        float fracX = localX - x;
        if (colHeight > 0 && localY >= colHeight) {
            float top = data.topLeft[x] + (data.topRight[x] - data.topLeft[x]) * fracX;
            if (localY <= top)
                return true;
        }

        if (data.columnBottoms == null || data.bottomLeft == null || data.bottomRight == null)
            return false;

        int colBottom = data.columnBottoms[x];
        if (colBottom >= 0 && localY <= colBottom) {
            float bottom = data.bottomLeft[x] + (data.bottomRight[x] - data.bottomLeft[x]) * fracX;
            if (localY >= bottom)
                return true;
        }

        if (data.rowLefts != null && data.leftBottom != null && data.leftTop != null) {
            int rowLeft = data.rowLefts[y];
            if (rowLeft >= 0 && localX < rowLeft) {
                float fracY = localY - y;
                float left = data.leftBottom[y] + (data.leftTop[y] - data.leftBottom[y]) * fracY;
                if (localX >= left)
                    return true;
            }
        }

        if (data.rowRights != null && data.rightBottom != null && data.rightTop != null) {
            int rowRight = data.rowRights[y];
            if (rowRight >= 0 && localX >= rowRight) {
                float fracY = localY - y;
                float right = data.rightBottom[y] + (data.rightTop[y] - data.rightBottom[y]) * fracY;
                if (localX <= right)
                    return true;
            }
        }

        return false;
    }

    private static boolean tryEnqueue(BlockGetter level, BlockPos origin, BlockSide side, int x, int y, int stride, boolean[] visited,
                                      int[] qx, int[] qy, BlockPos.MutableBlockPos bp, int idx) {
        int flat = y * stride + x;
        if (visited[flat])
            return false;

        if (!isScreenAt(level, origin.getX(), origin.getY(), origin.getZ(), side, x, y, bp))
            return false;

        visited[flat] = true;
        qx[idx] = x;
        qy[idx] = y;
        return true;
    }

    private static boolean isScreenAt(BlockGetter level, int ox, int oy, int oz, BlockSide side, int x, int y, BlockPos.MutableBlockPos bp) {
        bp.set(
            ox + side.right.x * x + side.up.x * y,
            oy + side.right.y * x + side.up.y * y,
            oz + side.right.z * x + side.up.z * y
        );
        return level.getBlockState(bp).getBlock() == BlockRegistry.SCREEN_BLOCk.get();
    }

    private static float[] buildBoundarySmoothLong(int[] heights) {
        int width = heights.length;
        float[] boundary = new float[width + 1];

        int i = 0;
        while (i < width) {
            if (heights[i] <= 0) {
                i++;
                continue;
            }

            int start = i;
            int end = i;
            int lastHeight = heights[i];
            while (end + 1 < width && heights[end + 1] > 0 && heights[end + 1] <= lastHeight) {
                end++;
                lastHeight = heights[end];
            }

            float startH = heights[start];
            float endH = heights[end];
            float span = (end + 1) - start;
            for (int j = start; j <= end + 1; j++) {
                float t = (j - start) / span;
                boundary[j] = startH + (endH - startH) * t;
            }

            i = end + 1;
        }

        return boundary;
    }

    private static float[] buildBoundarySmoothLongBottom(int[] bottoms) {
        int width = bottoms.length;
        float[] boundary = new float[width + 1];

        int i = 0;
        while (i < width) {
            if (bottoms[i] < 0) {
                i++;
                continue;
            }

            int start = i;
            int end = i;
            int lastHeight = bottoms[i];
            while (end + 1 < width && bottoms[end + 1] >= 0 && bottoms[end + 1] >= lastHeight) {
                end++;
                lastHeight = bottoms[end];
            }

            float startH = bottoms[start];
            float endH = bottoms[end];
            float span = (end + 1) - start;
            for (int j = start; j <= end + 1; j++) {
                float t = (j - start) / span;
                boundary[j] = startH + (endH - startH) * t;
            }

            i = end + 1;
        }

        return boundary;
    }

    private static float[] buildBoundarySmoothLongBottomReverse(int[] bottoms) {
        int width = bottoms.length;
        int[] reversed = new int[width];
        for (int i = 0; i < width; i++)
            reversed[i] = bottoms[width - 1 - i];

        float[] revBoundary = buildBoundarySmoothLongBottom(reversed);
        float[] boundary = new float[width + 1];
        for (int e = 0; e <= width; e++)
            boundary[e] = revBoundary[width - e];

        return boundary;
    }

    private static float[] buildBoundarySmoothLongReverse(int[] heights) {
        int width = heights.length;
        int[] reversed = new int[width];
        for (int i = 0; i < width; i++)
            reversed[i] = heights[width - 1 - i];

        float[] revBoundary = buildBoundarySmoothLong(reversed);
        float[] boundary = new float[width + 1];
        for (int e = 0; e <= width; e++)
            boundary[e] = revBoundary[width - e];

        return boundary;
    }

    private static Arc findCurveArc(boolean[] mask, int width, int height) {
        if (width < 2 && height < 2)
            return null;

        Endpoint[] endpoints = findEndpoints(mask, width, height);
        if (endpoints == null || endpoints.length != 2)
            return null;

        Endpoint e0 = endpoints[0];
        Endpoint e1 = endpoints[1];
        float p0x = e0.x + 0.5f;
        float p0y = e0.y + 0.5f;
        float p1x = e1.x + 0.5f;
        float p1y = e1.y + 0.5f;

        float t0x = e0.dx;
        float t0y = e0.dy;
        float t1x = e1.dx;
        float t1y = e1.dy;
        float t0len = (float) Math.sqrt(t0x * t0x + t0y * t0y);
        float t1len = (float) Math.sqrt(t1x * t1x + t1y * t1y);
        if (t0len <= 0.0001f || t1len <= 0.0001f)
            return null;

        t0x /= t0len;
        t0y /= t0len;
        t1x /= t1len;
        t1y /= t1len;

        float[][] normals0 = new float[][]{
            new float[]{-t0y, t0x},
            new float[]{t0y, -t0x}
        };
        float[][] normals1 = new float[][]{
            new float[]{-t1y, t1x},
            new float[]{t1y, -t1x}
        };

        Arc best = null;
        float bestSagitta = -1.0f;
        for (float[] n0 : normals0) {
            for (float[] n1 : normals1) {
                Arc arc = buildArcCandidate(mask, width, height, p0x, p0y, p1x, p1y, t0x, t0y, t1x, t1y, n0[0], n0[1], n1[0], n1[1]);
                if (arc == null)
                    continue;

                float chord = (float) Math.sqrt((p1x - p0x) * (p1x - p0x) + (p1y - p0y) * (p1y - p0y));
                float half = chord * 0.5f;
                float sagitta = arc.r - (float) Math.sqrt(Math.max(0.0f, arc.r * arc.r - half * half));
                if (sagitta > bestSagitta) {
                    bestSagitta = sagitta;
                    best = arc;
                }
            }
        }

        return best;
    }

    private static Arc buildArcCandidate(boolean[] mask, int width, int height,
                                         float p0x, float p0y, float p1x, float p1y,
                                         float t0x, float t0y, float t1x, float t1y,
                                         float n0x, float n0y, float n1x, float n1y) {
        float det = n0x * n1y - n0y * n1x;
        if (Math.abs(det) < 1.0e-5f)
            return null;

        float dx = p1x - p0x;
        float dy = p1y - p0y;
        float s = (dx * n1y - dy * n1x) / det;
        float cx = p0x + n0x * s;
        float cy = p0y + n0y * s;

        float r0x = p0x - cx;
        float r0y = p0y - cy;
        float r1x = p1x - cx;
        float r1y = p1y - cy;
        float r0 = (float) Math.sqrt(r0x * r0x + r0y * r0y);
        float r1 = (float) Math.sqrt(r1x * r1x + r1y * r1y);
        if (r0 <= 0.0001f || Math.abs(r0 - r1) > 0.01f)
            return null;

        float ccwTx = -r0y / r0;
        float ccwTy = r0x / r0;
        float cwTx = r0y / r0;
        float cwTy = -r0x / r0;
        float dotCcw = ccwTx * t0x + ccwTy * t0y;
        float dotCw = cwTx * t0x + cwTy * t0y;
        boolean ccw = dotCcw >= dotCw;

        float ccwT1x = -r1y / r1;
        float ccwT1y = r1x / r1;
        float cwT1x = r1y / r1;
        float cwT1y = -r1x / r1;
        float dotCcw1 = ccwT1x * t1x + ccwT1y * t1y;
        float dotCw1 = cwT1x * t1x + cwT1y * t1y;
        boolean ccw1 = dotCcw1 >= dotCw1;
        if (ccw != ccw1)
            return null;

        float start = (float) Math.atan2(r0y, r0x);
        float end = (float) Math.atan2(r1y, r1x);

        boolean useX = Math.abs(p1x - p0x) >= Math.abs(p1y - p0y);
        Arc arc = new Arc(cx, cy, r0, start, end, ccw, useX);
        if (arcHitsMask(arc, mask, width, height, e0Index(p0x, p0y, width), e0Index(p1x, p1y, width)))
            return null;

        return arc;
    }

    private static Endpoint[] findEndpoints(boolean[] mask, int width, int height) {
        Endpoint[] endpoints = new Endpoint[2];
        int count = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!mask[y * width + x])
                    continue;

                int neighbors = 0;
                int nx = 0;
                int ny = 0;
                boolean left = x > 0 && mask[y * width + (x - 1)];
                if (left) {
                    neighbors++;
                    nx = -1;
                    ny = 0;
                }
                boolean right = x + 1 < width && mask[y * width + (x + 1)];
                if (right) {
                    neighbors++;
                    nx = 1;
                    ny = 0;
                }
                boolean down = y > 0 && mask[(y - 1) * width + x];
                if (down) {
                    neighbors++;
                    nx = 0;
                    ny = -1;
                }
                boolean up = y + 1 < height && mask[(y + 1) * width + x];
                if (up) {
                    neighbors++;
                    nx = 0;
                    ny = 1;
                }
                if (x > 0 && y > 0 && mask[(y - 1) * width + (x - 1)] && !left && !down) {
                    neighbors++;
                    nx = -1;
                    ny = -1;
                }
                if (x + 1 < width && y > 0 && mask[(y - 1) * width + (x + 1)] && !right && !down) {
                    neighbors++;
                    nx = 1;
                    ny = -1;
                }
                if (x > 0 && y + 1 < height && mask[(y + 1) * width + (x - 1)] && !left && !up) {
                    neighbors++;
                    nx = -1;
                    ny = 1;
                }
                if (x + 1 < width && y + 1 < height && mask[(y + 1) * width + (x + 1)] && !right && !up) {
                    neighbors++;
                    nx = 1;
                    ny = 1;
                }

                if (neighbors == 1) {
                    if (count < 2)
                        endpoints[count] = new Endpoint(x, y, nx, ny);
                    count++;
                } else if (neighbors > 2) {
                    return null;
                }
            }
        }
        if (count != 2)
            return null;
        return endpoints;
    }

    private static boolean arcHitsMask(Arc arc, boolean[] mask, int width, int height, int skipA, int skipB) {
        int samples = 64;
        for (int i = 0; i <= samples; i++) {
            float t = i / (float) samples;
            float ang = interpolateAngle(arc.start, arc.end, arc.ccw, t);
            float x = arc.cx + arc.r * (float) Math.cos(ang);
            float y = arc.cy + arc.r * (float) Math.sin(ang);
            int ix = (int) Math.floor(x);
            int iy = (int) Math.floor(y);
            if (ix < 0 || iy < 0 || ix >= width || iy >= height)
                continue;
            int idx = iy * width + ix;
            if (idx == skipA || idx == skipB)
                continue;
            if (mask[idx])
                return true;
        }
        return false;
    }

    private static int e0Index(float px, float py, int width) {
        int ix = (int) Math.floor(px);
        int iy = (int) Math.floor(py);
        if (ix < 0 || iy < 0)
            return -1;
        return iy * width + ix;
    }

    private static float interpolateAngle(float start, float end, boolean ccw, float t) {
        float a = start;
        float b = end;
        if (ccw) {
            if (b < a)
                b += (float) (Math.PI * 2.0);
            return a + (b - a) * t;
        } else {
            if (b > a)
                b -= (float) (Math.PI * 2.0);
            return a + (b - a) * t;
        }
    }

    private static boolean angleBetween(float ang, float start, float end, boolean ccw) {
        float a = start;
        float b = end;
        float x = ang;
        if (ccw) {
            if (b < a) b += (float) (Math.PI * 2.0);
            if (x < a) x += (float) (Math.PI * 2.0);
            return x >= a && x <= b;
        } else {
            if (b > a) b -= (float) (Math.PI * 2.0);
            if (x > a) x -= (float) (Math.PI * 2.0);
            return x <= a && x >= b;
        }
    }

    private static float[] sampleCurveByX(Arc arc, int width) {
        float[] curve = new float[width];
        for (int i = 0; i < width; i++)
            curve[i] = Float.NaN;

        for (int x = 0; x < width; x++) {
            float dx = (x + 0.5f) - arc.cx;
            float dist2 = arc.r * arc.r - dx * dx;
            if (dist2 < 0.0f)
                continue;
            float dy = (float) Math.sqrt(dist2);
            float y1 = arc.cy + dy;
            float y2 = arc.cy - dy;
            float ang1 = (float) Math.atan2(y1 - arc.cy, dx);
            float ang2 = (float) Math.atan2(y2 - arc.cy, dx);
            if (angleBetween(ang1, arc.start, arc.end, arc.ccw))
                curve[x] = y1;
            else if (angleBetween(ang2, arc.start, arc.end, arc.ccw))
                curve[x] = y2;
        }

        return curve;
    }

    private static float[] sampleCurveByY(Arc arc, int height) {
        float[] curve = new float[height];
        for (int i = 0; i < height; i++)
            curve[i] = Float.NaN;

        for (int y = 0; y < height; y++) {
            float dy = (y + 0.5f) - arc.cy;
            float dist2 = arc.r * arc.r - dy * dy;
            if (dist2 < 0.0f)
                continue;
            float dx = (float) Math.sqrt(dist2);
            float x1 = arc.cx + dx;
            float x2 = arc.cx - dx;
            float ang1 = (float) Math.atan2(dy, x1 - arc.cx);
            float ang2 = (float) Math.atan2(dy, x2 - arc.cx);
            if (angleBetween(ang1, arc.start, arc.end, arc.ccw))
                curve[y] = x1;
            else if (angleBetween(ang2, arc.start, arc.end, arc.ccw))
                curve[y] = x2;
        }

        return curve;
    }

    private static boolean isTopCurve(float[] curve, int[] heights, int[] bottoms) {
        int top = 0;
        int bottom = 0;
        for (int i = 0; i < curve.length; i++) {
            float v = curve[i];
            if (Float.isNaN(v))
                continue;
            if (v > heights[i])
                top++;
            else if (bottoms[i] >= 0 && v < bottoms[i])
                bottom++;
        }
        return top >= bottom;
    }

    private static boolean isLeftCurve(float[] curve, int[] rowLefts, int[] rowRights) {
        int left = 0;
        int right = 0;
        for (int i = 0; i < curve.length; i++) {
            float v = curve[i];
            if (Float.isNaN(v))
                continue;
            if (rowLefts[i] >= 0 && v < rowLefts[i])
                left++;
            else if (rowRights[i] >= 0 && v > rowRights[i])
                right++;
        }
        return left >= right;
    }

    private static boolean tryEnqueueRelative(BlockGetter level, BlockPos start, BlockSide side, int x, int y, int stride, int originX, int originY,
                                              boolean[] visited, int[] qx, int[] qy, BlockPos.MutableBlockPos bp, int idx) {
        int ix = x + originX;
        int iy = y + originY;
        int rangeY = visited.length / stride;
        if (ix < 0 || iy < 0)
            return false;
        if (ix >= stride || iy >= rangeY)
            return false;

        int flat = iy * stride + ix;
        if (visited[flat])
            return false;

        if (!isScreenAt(level, start.getX(), start.getY(), start.getZ(), side, x, y, bp))
            return false;

        visited[flat] = true;
        qx[idx] = x;
        qy[idx] = y;
        return true;
    }
}
