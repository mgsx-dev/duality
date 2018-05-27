package net.mgsx.dl3.screens;

import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.utils.AnimationController;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Action;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;

import net.mgsx.dl3.model.EnemyPart;

public class Level2Screen extends BattleScreen
{
	private ModelInstance groundModel;
	private EnemyPart frontPart;
	private EnemyPart handLeftPart;
	private EnemyPart handRightPart;
	private EnemyPart legAPart;
	private EnemyPart legBPart;
	private EnemyPart legCPart;
	private EnemyPart backLeftPart;
	private EnemyPart backRightPart;
	private EnemyPart internalPart;
	
	private Actor emitActor = new Actor();

	public Level2Screen() {
		super("level2.g3dj");
		
		models.add(groundModel = new ModelInstance(levelModel, "Ground"));
		
		collisionSystem.attachEntity(groundModel);
		
		frontPart = createPart("front.emit", "Front", false);
		handLeftPart = createPart("hand.l", "HandLeft", true);
		handRightPart = createPart("hand.r", "HandRight", false);
		legAPart = createPart(null, "LegA", false);
		legBPart = createPart(null, "LegB", true);
		legCPart = createPart(null, "LegC", true);
		backLeftPart = createPart("back.emit.l", "BackLeft", false);
		backRightPart = createPart("back.emit.r", "BackRight", true);
		internalPart = createPart(null, "Internal", null);
		
		internalPart.material = bossModel.getMaterial("Boss");
		
		
		bossActor.addAction(
				Actions.sequence(
						
						animate(bossAnimator, "Boss", "Born", -1f),
						animate(bossAnimator, "Boss", "Rugis", 2f),
						animate(bossAnimator, "Boss", "Rugis", 2f),
					
						Actions.repeat(-1, Actions.sequence(
								
								animate(bossAnimator, "Boss", "BaseMove", 0.2f),
								animate(bossAnimator, "Boss", "Jump", 0.6f),
								animate(bossAnimator, "Boss", "OpenClose", 1f),
								Actions.delay(2),
								animate(bossAnimator, "Boss", "BaseMove", 0.5f),
								Actions.repeat(4, 
										action(bossAnimator, "Boss", "Rugis", 1.5f)),
								animate(bossAnimator, "Boss", "BaseMove", 0.2f),
								
								animate(bossAnimator, "Boss", "OpenClose", -1f),
								
								animate(bossAnimator, "Boss", "Turn60", 1f),
								turn(bossAnimator, "Boss", "BaseMove", .2f)
						)),
						
						animate(bossAnimator, "Boss", "Dead", 1f)
			));
		
		
		emitActor.addAction(Actions.sequence(
				Actions.delay(4)
				// TODO pre attack
				));
		
	}
	
	private Action rotate(final AnimationController animator, final Vector3 axis, final float degrees) {
		return Actions.run(new Runnable() {
			
			@Override
			public void run() {
				animator.target.transform.rotate(axis, degrees);
				// animator.target.calculateTransforms();
			}
		});
	}

	private EnemyPart createPart(String boneID, String materialID, Boolean light){
		EnemyPart part = new EnemyPart();
		part.model = bossModel;
		part.node = boneID == null ? null : bossModel.getNode(boneID, true);
		part.material = bossModel.getMaterial(materialID);
		part.id = collisionSystem.attachEntity(bossModel, materialID);
		part.energy = part.energyMax = PART_ENERGY;
		part.light = light;
		enemyParts.add(part);
		return part;
	}
	
	@Override
	public void render(float delta) {
		super.render(delta);
		
		emitActor.act(delta);
		if(!emitActor.hasActions()){
			chooseAction();
		}
	}
	
	private int emitCount = 0;
	
	private void chooseAction() {
		Actor actor = emitActor;
		emitCount++;
		if(emitCount % 2 == 0){
			actor.addAction(Actions.delay(MathUtils.lerp(0f, 4, bossLife)));
		}
		
		int rnd = MathUtils.random(3);
		switch(rnd){
		case 1:
			sequenceBackLeft(actor);
			sequenceBackRight(actor);
			break;
		case 2:
			sequenceTentacleLeft(actor, 4, 4);
			break;
		case 3:
			sequenceFront(actor);
			break;
		case 0:
			sequenceTentacleLeft(actor, 2, 1);
			sequenceTentacleRight(actor, 2, 1);
			break;
		default:
			sequenceTentacleRight(actor, 2, 4);
			
			break;
		}
		
		
		
	}

	private void sequenceFront(Actor actor)
	{
		actor.addAction(Actions.repeat(5, Actions.sequence(
				Actions.delay(2),
				Actions.repeat(6, Actions.sequence(
						emit("front.emit", "EyeDark", false),
						Actions.delay(.3f))),
				Actions.delay(2),
				Actions.repeat(6, Actions.sequence(
						emit("front.emit", "EyeLight", true),
						Actions.delay(.3f)))
			)));
	}
	
	private void sequenceTentacleLeft(Actor actor, int count1, int count2)
	{
		actor.addAction(Actions.repeat(count1, Actions.sequence(
				Actions.delay(.5f),
				Actions.repeat(count2, Actions.sequence(
						emit("hand.l", "EyeLight", true),
						Actions.delay(.2f)))
			)));
	}
	
	private void sequenceTentacleRight(Actor actor, int count1, int count2)
	{
		actor.addAction(Actions.repeat(count1, Actions.sequence(
				Actions.delay(.5f),
				Actions.repeat(count2, Actions.sequence(
						emit("hand.r", "EyeDark", false),
						Actions.delay(.2f)))
			)));
	}
	
	private void sequenceBackLeft(Actor actor)
	{
		actor.addAction(Actions.repeat(2, Actions.sequence(
				Actions.delay(.5f),
				Actions.repeat(4, Actions.sequence(
						emit("back.emit.l", "EyeDark", false),
						Actions.delay(.3f))),
				Actions.delay(.5f)
			)));
	}
	
	private void sequenceBackRight(Actor actor)
	{
		actor.addAction(Actions.repeat(2, Actions.sequence(
				Actions.delay(2),
				Actions.repeat(4, Actions.sequence(
						emit("back.emit.r", "EyeLight", true),
						Actions.delay(.3f)))
			)));
	}
}
