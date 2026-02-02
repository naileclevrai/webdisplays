/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.data;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.montoyo.wd.net.BufferUtils;
import net.montoyo.wd.utilities.data.BlockSide;
import net.montoyo.wd.utilities.math.Vector3i;

public class SetURLData extends GuiData {
    public Vector3i pos;
    public BlockSide side;
    public String url;
    public boolean isRemote;
    public Vector3i remoteLocation;

    public SetURLData() {
    }

    public SetURLData(Vector3i pos, BlockSide side, String url) {
        this.pos = pos;
        this.side = side;
        this.url = url;
        isRemote = false;
        remoteLocation = new Vector3i();
    }

    public SetURLData(Vector3i pos, BlockSide side, String url, BlockPos rl) {
        this.pos = pos;
        this.side = side;
        this.url = url;
        isRemote = true;
        remoteLocation = new Vector3i(rl);
    }


    @Override
    public String getName() {
        return "SetURL";
    }

    @Override
    public void serialize(FriendlyByteBuf buf) {
        BufferUtils.writeVec3i(buf, pos);
        BufferUtils.writeEnum(buf, side, (byte) 1);
        buf.writeUtf(url);
        buf.writeBoolean(isRemote);
        if (isRemote) BufferUtils.writeVec3i(buf, remoteLocation);
    }

    @Override
    public void deserialize(FriendlyByteBuf buf) {
        pos = BufferUtils.readVec3i(buf);
        side = (BlockSide) BufferUtils.readEnum(buf, (v) -> BlockSide.values()[v], (byte) 1);
        url = buf.readUtf();
        isRemote = buf.readBoolean();
        if (isRemote) remoteLocation = BufferUtils.readVec3i(buf);
        else remoteLocation = new Vector3i();
    }
}
