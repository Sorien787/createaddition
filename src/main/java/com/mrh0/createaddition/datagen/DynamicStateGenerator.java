package com.mrh0.createaddition.datagen;

import javax.annotation.Nullable;


import com.simibubi.create.foundation.data.SpecialBlockStateGen;
import com.tterrag.registrate.providers.DataGenContext;
import com.tterrag.registrate.providers.RegistrateBlockstateProvider;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraftforge.client.model.generators.ModelFile;

public class DynamicStateGenerator extends SpecialBlockStateGen {

    private final @Nullable EnumProperty<?> typeDelegate;
    private final @Nullable String customFolder;




    public DynamicStateGenerator(EnumProperty<?> typeDelegate) {
        this.typeDelegate = typeDelegate;
        customFolder = null;
    }

    @Override
    protected int getXRotation(BlockState state) {
        return DirectionTransformer.getRotation(state).x();
    }

    @Override
    protected int getYRotation(BlockState state) {
        return DirectionTransformer.getRotation(state).y();
    }

    @Override
    public <T extends Block> ModelFile getModel(DataGenContext<Block, T> ctx,
        RegistrateBlockstateProvider provider, BlockState state) {

        String typeName = (typeDelegate == null) ? "base" : 
            state.getValue(typeDelegate).getSerializedName();

        String orientSuffix = 
            (DirectionTransformer.isDistinctionRequired(state) &&
            DirectionTransformer.isHorizontal(state)) 
            ? "_side" : "";

        if(customFolder == null)
            return provider.models().getExistingFile(extend(ctx,
                typeName + orientSuffix));

        return provider.models().getExistingFile(extend(ctx,
            customFolder, typeName + orientSuffix));

    }

    public static ResourceLocation extend(DataGenContext<?, ?> ctx, String folder) {
        return extend(ctx, "block", folder);
    }

    public static ResourceLocation extend(DataGenContext<?, ?> ctx, String root, String folder) {
        String path = root + "/" + ctx.getId().getPath() + "/" + folder;
        return new ResourceLocation(ctx.getId().getNamespace(), path);
    }
}
