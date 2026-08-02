package com.jammerbam.zomboid.client.model;

// Made with Blockbench 5.1.6
// Exported for Minecraft version 1.7 - 1.12
// Imported into Zomboid and augmented with procedural vanilla-style animation.

import com.jammerbam.zomboid.entity.EntityBuffZombie;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
public class ModelBuffZombie extends ModelBase {
	private final ModelRenderer buff_zombie;
	private final ModelRenderer body;
	private final ModelRenderer head;
	private final ModelRenderer headwear;
	private final ModelRenderer left_arm;
	private final ModelRenderer right_arm;
	private final ModelRenderer spine;
	private final ModelRenderer ribs;
	private final ModelRenderer left_leg;
	private final ModelRenderer right_leg;

	public ModelBuffZombie() {
		textureWidth = 64;
		textureHeight = 64;

		buff_zombie = new ModelRenderer(this);
		buff_zombie.setRotationPoint(0.0F, 0.0F, 0.0F);


		body = new ModelRenderer(this);
		body.setRotationPoint(0.0F, 0.0F, 0.0F);
		buff_zombie.addChild(body);
		body.cubeList.add(new ModelBox(body, 16, 16, -4.0F, -2.0F, -2.0F, 8, 12, 4, 0.0F, false));
		body.cubeList.add(new ModelBox(body, 12, 10, -3.0F, -4.0F, -2.0F, 6, 2, 4, 0.0F, false));
		body.cubeList.add(new ModelBox(body, 18, 20, -5.0F, -2.0F, -3.0F, 10, 4, 1, 0.0F, false));
		body.cubeList.add(new ModelBox(body, 20, 23, -3.0F, 2.0F, -3.0F, 6, 6, 1, 0.0F, false));
		body.cubeList.add(new ModelBox(body, 16, 22, 4.0F, -2.0F, -2.0F, 2, 4, 4, 0.0F, false));
		body.cubeList.add(new ModelBox(body, 16, 22, -6.0F, -2.0F, -2.0F, 2, 4, 4, 0.0F, false));
		body.cubeList.add(new ModelBox(body, 19, 22, -5.0F, 2.0F, -2.0F, 1, 4, 4, 0.0F, false));
		body.cubeList.add(new ModelBox(body, 16, 17, 4.0F, 2.0F, -2.0F, 1, 4, 4, 0.0F, false));
		body.cubeList.add(new ModelBox(body, 7, 3, -6.0F, -3.0F, 0.0F, 3, 1, 2, 0.0F, false));
		body.cubeList.add(new ModelBox(body, 7, 3, 3.0F, -3.0F, 0.0F, 3, 1, 2, 0.0F, false));
		body.cubeList.add(new ModelBox(body, 9, 3, -4.0F, -4.0F, 0.0F, 1, 1, 2, 0.0F, false));
		body.cubeList.add(new ModelBox(body, 9, 3, 3.0F, -4.0F, 0.0F, 1, 1, 2, 0.0F, false));
		body.cubeList.add(new ModelBox(body, 21, 18, -3.0F, -4.0F, 2.0F, 2, 1, 1, 0.0F, false));
		body.cubeList.add(new ModelBox(body, 21, 18, 1.0F, -4.0F, 2.0F, 2, 1, 1, 0.0F, false));

		head = new ModelRenderer(this);
		head.setRotationPoint(0.0F, -4.0F, 0.0F);
		body.addChild(head);
		head.cubeList.add(new ModelBox(head, 0, 0, -4.0F, -8.0F, -4.0F, 8, 8, 8, 0.0F, false));

		headwear = new ModelRenderer(this);
		headwear.setRotationPoint(0.0F, -4.0F, 0.0F);
		body.addChild(headwear);
		headwear.cubeList.add(new ModelBox(headwear, 32, 0, -4.0F, -8.0F, -4.0F, 8, 8, 8, 0.5F, false));

		left_arm = new ModelRenderer(this);
		left_arm.setRotationPoint(6.0F, -1.0F, 0.0F);
		body.addChild(left_arm);
		left_arm.cubeList.add(new ModelBox(left_arm, 40, 16, 0.0F, -2.0F, -2.0F, 4, 13, 4, 0.0F, true));
		left_arm.cubeList.add(new ModelBox(left_arm, 43, 19, 0.0F, -1.0F, -3.0F, 4, 6, 1, 0.0F, false));
		left_arm.cubeList.add(new ModelBox(left_arm, 43, 16, 4.0F, -1.0F, -2.0F, 1, 6, 4, 0.0F, false));
		left_arm.cubeList.add(new ModelBox(left_arm, 42, 19, 0.0F, -1.0F, 2.0F, 4, 6, 1, 0.0F, false));

		right_arm = new ModelRenderer(this);
		right_arm.setRotationPoint(-6.0F, -1.0F, 0.0F);
		body.addChild(right_arm);
		right_arm.cubeList.add(new ModelBox(right_arm, 40, 16, -4.0F, -2.0F, -2.0F, 4, 13, 4, 0.0F, false));
		right_arm.cubeList.add(new ModelBox(right_arm, 42, 19, -4.0F, -1.0F, 2.0F, 4, 6, 1, 0.0F, false));
		right_arm.cubeList.add(new ModelBox(right_arm, 43, 16, -5.0F, -1.0F, -2.0F, 1, 6, 4, 0.0F, false));
		right_arm.cubeList.add(new ModelBox(right_arm, 43, 19, -4.0F, -1.0F, -3.0F, 4, 6, 1, 0.0F, false));

		spine = new ModelRenderer(this);
		spine.setRotationPoint(1.0F, 7.0F, 3.0F);
		body.addChild(spine);
		spine.cubeList.add(new ModelBox(spine, 34, 17, -2.0F, -11.0F, -1.0F, 2, 12, 1, 0.0F, false));

		ribs = new ModelRenderer(this);
		ribs.setRotationPoint(0.0F, 0.0F, 0.0F);
		spine.addChild(ribs);
		ribs.cubeList.add(new ModelBox(ribs, 31, 21, -6.0F, -9.0F, -1.0F, 4, 1, 1, 0.0F, false));
		ribs.cubeList.add(new ModelBox(ribs, 31, 21, 0.0F, -9.0F, -1.0F, 4, 1, 1, 0.0F, false));
		ribs.cubeList.add(new ModelBox(ribs, 31, 21, -5.0F, -7.0F, -1.0F, 3, 1, 1, 0.0F, false));
		ribs.cubeList.add(new ModelBox(ribs, 31, 21, 0.0F, -7.0F, -1.0F, 3, 1, 1, 0.0F, false));
		ribs.cubeList.add(new ModelBox(ribs, 31, 21, 0.0F, -5.0F, -1.0F, 2, 1, 1, 0.0F, false));
		ribs.cubeList.add(new ModelBox(ribs, 31, 21, -4.0F, -5.0F, -1.0F, 2, 1, 1, 0.0F, false));
		ribs.cubeList.add(new ModelBox(ribs, 31, 21, 0.0F, -3.0F, -1.0F, 2, 1, 1, 0.0F, false));
		ribs.cubeList.add(new ModelBox(ribs, 31, 21, -4.0F, -3.0F, -1.0F, 2, 1, 1, 0.0F, false));
		ribs.cubeList.add(new ModelBox(ribs, 31, 21, -3.0F, -1.0F, -1.0F, 1, 1, 1, 0.0F, false));
		ribs.cubeList.add(new ModelBox(ribs, 31, 21, 0.0F, -1.0F, -1.0F, 1, 1, 1, 0.0F, false));

		left_leg = new ModelRenderer(this);
		left_leg.setRotationPoint(1.9F, 12.0F, 0.0F);
		buff_zombie.addChild(left_leg);
		left_leg.cubeList.add(new ModelBox(left_leg, 0, 16, -1.9F, -2.0F, -2.0F, 4, 14, 4, 0.0F, true));

		right_leg = new ModelRenderer(this);
		right_leg.setRotationPoint(-1.9F, 12.0F, 0.0F);
		buff_zombie.addChild(right_leg);
		right_leg.cubeList.add(new ModelBox(right_leg, 0, 16, -2.1F, -2.0F, -2.0F, 4, 14, 4, 0.0F, false));
	}

	@Override
	public void render(Entity entity, float f, float f1, float f2, float f3, float f4, float f5) {
		setRotationAngles(f, f1, f2, f3, f4, f5, entity);
		buff_zombie.render(f5);
	}

	@Override
	public void setRotationAngles(float limbSwing, float limbSwingAmount, float ageInTicks,
			float netHeadYaw, float headPitch, float scaleFactor, Entity entity) {
		float degreesToRadians = (float) Math.PI / 180.0F;
		head.rotateAngleY = netHeadYaw * degreesToRadians;
		head.rotateAngleX = headPitch * degreesToRadians;
		headwear.rotateAngleY = head.rotateAngleY;
		headwear.rotateAngleX = head.rotateAngleX;

		right_leg.rotateAngleX =
			MathHelper.cos(limbSwing * 0.6662F) * 1.4F * limbSwingAmount;
		left_leg.rotateAngleX =
			MathHelper.cos(limbSwing * 0.6662F + (float) Math.PI) * 1.4F * limbSwingAmount;
		right_leg.rotateAngleY = 0.0F;
		left_leg.rotateAngleY = 0.0F;

		boolean chasingPlayer =
			entity instanceof EntityBuffZombie && ((EntityBuffZombie) entity).isChasingPlayer();
		if (chasingPlayer) {
			float attackSwing = MathHelper.sin(swingProgress * (float) Math.PI);
			float attackCurve = MathHelper.sin(
				(1.0F - (1.0F - swingProgress) * (1.0F - swingProgress)) * (float) Math.PI
			);
			right_arm.rotateAngleY = -(0.1F - attackSwing * 0.6F);
			left_arm.rotateAngleY = 0.1F - attackSwing * 0.6F;
			right_arm.rotateAngleX = -(float) Math.PI / 2.0F;
			left_arm.rotateAngleX = -(float) Math.PI / 2.0F;
			right_arm.rotateAngleX -= attackSwing * 1.2F - attackCurve * 0.4F;
			left_arm.rotateAngleX -= attackSwing * 1.2F - attackCurve * 0.4F;
		} else {
			right_arm.rotateAngleY = 0.0F;
			left_arm.rotateAngleY = 0.0F;
			right_arm.rotateAngleX =
				MathHelper.cos(limbSwing * 0.6662F + (float) Math.PI) * limbSwingAmount;
			left_arm.rotateAngleX =
				MathHelper.cos(limbSwing * 0.6662F) * limbSwingAmount;
		}
		right_arm.rotateAngleZ = 0.0F;
		left_arm.rotateAngleZ = 0.0F;
		right_arm.rotateAngleZ += MathHelper.cos(ageInTicks * 0.09F) * 0.05F + 0.05F;
		left_arm.rotateAngleZ -= MathHelper.cos(ageInTicks * 0.09F) * 0.05F + 0.05F;
		right_arm.rotateAngleX += MathHelper.sin(ageInTicks * 0.067F) * 0.05F;
		left_arm.rotateAngleX -= MathHelper.sin(ageInTicks * 0.067F) * 0.05F;
	}

	public void setRotationAngle(ModelRenderer modelRenderer, float x, float y, float z) {
		modelRenderer.rotateAngleX = x;
		modelRenderer.rotateAngleY = y;
		modelRenderer.rotateAngleZ = z;
	}
}
