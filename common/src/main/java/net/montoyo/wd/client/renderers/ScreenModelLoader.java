package net.montoyo.wd.client.renderers;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.datafixers.util.Pair;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.model.geometry.IGeometryBakingContext;
import net.minecraftforge.client.model.geometry.IGeometryLoader;
import net.minecraftforge.client.model.geometry.IUnbakedGeometry;
import net.montoyo.wd.utilities.data.ScreenPieceType;

import java.util.function.Function;

public class ScreenModelLoader implements IGeometryLoader<ScreenModelLoader.ScreenModelGeometry> {
    public static final ResourceLocation SCREEN_LOADER = new ResourceLocation("webdisplays", "screen_loader");

    public static final ResourceLocation SCREEN_SIDE = new ResourceLocation("webdisplays", "block/screen");

    private static final ResourceLocation[] SIDES = new ResourceLocation[16];
    public static final Material[] MATERIALS_SIDES = new Material[16];
    
    static {
        for (int i = 0; i < SIDES.length; i++) {
            SIDES[i] = new ResourceLocation(SCREEN_SIDE.getNamespace(), SCREEN_SIDE.getPath() + i);
            MATERIALS_SIDES[i] = ForgeHooksClient.getBlockMaterial(SIDES[i]);
        }
    }
    
    @Override
    public ScreenModelGeometry read(JsonObject jsonObject, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        ScreenPieceType defaultPiece = ScreenPieceType.FULL;
        if (jsonObject.has("default_piece")) {
            String pieceName = GsonHelper.getAsString(jsonObject, "default_piece");
            for (ScreenPieceType piece : ScreenPieceType.values()) {
                if (piece.getSerializedName().equals(pieceName)) {
                    defaultPiece = piece;
                    break;
                }
            }
        }
        return new ScreenModelGeometry(defaultPiece);
    }

    public static class ScreenModelGeometry implements IUnbakedGeometry<ScreenModelGeometry> {
        private final ScreenPieceType defaultPiece;

        public ScreenModelGeometry(ScreenPieceType defaultPiece) {
            this.defaultPiece = defaultPiece;
        }

        @Override
        public BakedModel bake(IGeometryBakingContext context, ModelBaker baker, Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelState, ItemOverrides overrides, ResourceLocation modelLocation) {
            return new ScreenBaker(modelState, spriteGetter, overrides, context.getTransforms(), defaultPiece);
        }
        
//        @Override
//        public void resolveParents(Function<ResourceLocation, UnbakedModel> modelGetter, IGeometryBakingContext context) {
//            IUnbakedGeometry.super.resolveParents(modelGetter, context);
//        }
        
//        @Override
//        public Set<String> getConfigurableComponentNames() {
//            return IUnbakedGeometry.super.getConfigurableComponentNames();
//        }

        // TODO: ?
//        @Override
//        public Collection<Material> getMaterials(IGeometryBakingContext iGeometryBakingContext, Function<ResourceLocation, UnbakedModel> function, Set<Pair<String, String>> set) {
//            return Arrays.asList(MATERIALS_SIDES);
//        }
    }
}


