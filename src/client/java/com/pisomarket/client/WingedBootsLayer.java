package com.pisomarket.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;

// First pass at real 3D wings on WingedBootsContent.WINGED_BOOTS, attached
// at the ankle rather than a flat texture decal (see the comment on
// WingedBootsContent for why the decal attempt was rejected).
//
// Built the same way vanilla's own Elytra model is (two flat boxes,
// PartPose-rotated outward) — that is the closest existing vanilla example
// of "extra 3D geometry attached to and moving with a body part," so this
// copies its shape, just much smaller and anchored to the leg instead of
// the torso. Reuses vanilla's own elytra texture rather than a new PNG —
// it is already a plausible feather pattern and needs no new art.
//
// UNVERIFIED VISUALLY. This is the first custom render layer in this
// project — everything else (including the PvP health display) deliberately
// avoided one. Position, scale and rotation below are a first guess against
// the vanilla leg model's known dimensions, not something confirmed in
// game yet. Expect to tweak WING_Y / WING_SCALE after actually looking at
// it in TLauncher.
public final class WingedBootsLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private static final Identifier ELYTRA_TEXTURE =
			Identifier.fromNamespaceAndPath("minecraft", "textures/entity/elytra.png");

	// Vanilla legs are 4 wide x 12 tall x 4 deep, origin (rotation pivot) at
	// the hip. The ankle is near the bottom of that, hence Y close to 12.
	private static final float WING_Y = 9.0F;
	private static final float WING_OUTWARD = 2.0F;

	private final ModelPart leftWing;
	private final ModelPart rightWing;

	public WingedBootsLayer(final RenderLayerParent<AvatarRenderState, PlayerModel> renderer, final ModelPart root) {
		super(renderer);
		this.leftWing = root.getChild("left_wing");
		this.rightWing = root.getChild("right_wing");
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		// Small flat boxes, same texOffs region the vanilla elytra model
		// reads its feather texture from, just a much smaller box than the
		// full-size wing (4 wide x 6 tall x 1 thick, vs elytra's 10x20x2).
		root.addOrReplaceChild(
				"left_wing",
				CubeListBuilder.create().texOffs(22, 0).addBox(-4.0F, 0.0F, 0.0F, 4.0F, 6.0F, 1.0F),
				PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, (float) (-Math.PI / 6))
		);
		root.addOrReplaceChild(
				"right_wing",
				CubeListBuilder.create().texOffs(22, 0).mirror().addBox(0.0F, 0.0F, 0.0F, 4.0F, 6.0F, 1.0F),
				PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, (float) (Math.PI / 6))
		);
		return LayerDefinition.create(mesh, 64, 32);
	}

	@Override
	public void submit(
			final PoseStack poseStack, final SubmitNodeCollector submitNodeCollector, final int lightCoords,
			final AvatarRenderState state, final float yRot, final float xRot
	) {
		if (state.feetEquipment.getItem() != com.pisomarket.combat.WingedBootsContent.WINGED_BOOTS) {
			return;
		}

		PlayerModel parent = this.getParentModel();

		poseStack.pushPose();
		parent.leftLeg.translateAndRotate(poseStack);
		poseStack.translate(-WING_OUTWARD / 16.0, WING_Y / 16.0, 0.0);
		WingModel.render(this.leftWing, ELYTRA_TEXTURE, poseStack, submitNodeCollector, lightCoords, state);
		poseStack.popPose();

		poseStack.pushPose();
		parent.rightLeg.translateAndRotate(poseStack);
		poseStack.translate(WING_OUTWARD / 16.0, WING_Y / 16.0, 0.0);
		WingModel.render(this.rightWing, ELYTRA_TEXTURE, poseStack, submitNodeCollector, lightCoords, state);
		poseStack.popPose();
	}

	// A single ModelPart isn't itself a Model — renderColoredCutoutModel
	// needs a Model to call setupAnim/renderToBuffer on. This tiny wrapper
	// exists only so one ModelPart (already positioned) can go through that
	// same helper without building a second full EntityModel per wing.
	private static final class WingModel extends EntityModel<AvatarRenderState> {
		private WingModel(final ModelPart root) {
			super(root);
		}

		static void render(
				final ModelPart wing, final Identifier texture, final PoseStack poseStack,
				final SubmitNodeCollector submitNodeCollector, final int lightCoords, final AvatarRenderState state
		) {
			RenderLayer.renderColoredCutoutModel(
					new WingModel(wing), texture, poseStack, submitNodeCollector, lightCoords, state, -1, 0
			);
		}
	}
}
