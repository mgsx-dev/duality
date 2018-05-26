package net.mgsx.dl3.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.loader.G3dModelLoader;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.utils.AnimationController;
import com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationDesc;
import com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationListener;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Action;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.JsonReader;

import net.mgsx.dl3.model.Mob;

public class BattleScreen extends ScreenAdapter
{
	private Camera camera;
	private ModelBatch batch;
	private Array<ModelInstance> models = new Array<ModelInstance>();
	private float time;
	private Vector3 cameraPosition = new Vector3();
	private Environment env;
	private Model levelModel;
	private ModelInstance bossModel;
	private AnimationController bossAnimator;
	private Actor bossActor;
	private Array<Mob> mobs = new Array<Mob>();
	private float cameraAngle;

	public BattleScreen() {
		camera = new PerspectiveCamera(60f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		camera.position.set(2, 2, 2);
		camera.lookAt(Vector3.Zero);
		camera.update();
		batch = new ModelBatch();
		
		levelModel = new G3dModelLoader(new JsonReader()).loadModel(Gdx.files.internal("level1.g3dj"));
		
//		Model model = new ModelBuilder()
//		.createBox(1f, 1f, 1f, 
//				new Material(new ColorAttribute(ColorAttribute.Diffuse, Color.WHITE)), 
//				new VertexAttributes(VertexAttribute.Position()).getMask());
		models.add(bossModel = new ModelInstance(levelModel, "Boss", "BossMesh"));
		
		bossAnimator = new AnimationController(bossModel);
		
		bossActor = new Actor();
		
		bossActor.addAction(Actions.repeat(-1, Actions.sequence(
			Actions.delay(2),
			animate(bossAnimator, "Boss", "Clap1Open", 1),
			Actions.repeat(3, Actions.sequence(
					emit("Emit1", "Eye"),
					Actions.delay(1))),
			Actions.delay(2),
			animate(bossAnimator, "Boss", "Clap1Open", -1)
		)));
		
		env = new Environment();
		env.add(new DirectionalLight().set(Color.WHITE, new Vector3(1, -3, 1).nor()));
		env.set(new ColorAttribute(ColorAttribute.AmbientLight, new Color(Color.WHITE).mul(.5f)));
	}
	
	private Action emit(final String emitterID, final String emittedID) 
	{
		return Actions.run(new Runnable() {
			@Override
			public void run() {
				Node emitter = bossModel.getNode(emitterID, true);
				ModelInstance model = new ModelInstance(levelModel, emittedID);
				model.nodes.first().translation.setZero();
				model.calculateTransforms();
				Mob mob = new Mob();
				mob.model = model;
				mob.position.setZero().mul(emitter.globalTransform);
				mob.direction.set(Vector3.Y).rot(emitter.globalTransform);
				mobs.add(mob);
			}
		});
	}

	private Action animate(final AnimationController animator, String nodeNameID, String animationID, final float speed){
		final String id = nodeNameID + "|" + animationID;
		Action action = new Action() {
			private AnimationDesc animation;
			private boolean end;
			private AnimationListener listener = new AnimationListener() {
				
				@Override
				public void onLoop(AnimationDesc animation) {
				}
				
				@Override
				public void onEnd(AnimationDesc animation) {
					end = true;
				}
			};
			@Override
			public void restart() {
				animation = null;
				end = false;
			}
			@Override
			public boolean act(float delta) {
				if(animation == null){
					animation = animator.action(id, 1, speed, listener, 0);
				}
				return end;
			}
		};
		return action;
	}
	
	@Override
	public void render(float delta) 
	{
		time += delta;
		
		float cameraDistance = 10;
		float camAngleSpeed = 30;
		
		if(isLeft()){
			cameraAngle -= delta * camAngleSpeed;
		}
		else if(isRight()){
			cameraAngle += delta * camAngleSpeed;
		}
		// XXX cameraAngle = time * 10f;
		
		cameraPosition.x = MathUtils.cosDeg(cameraAngle) * cameraDistance;
		cameraPosition.z = MathUtils.sinDeg(cameraAngle) * cameraDistance;
		cameraPosition.y = 10;
		
		camera.position.set(cameraPosition);
		camera.up.set(Vector3.Y);
		camera.lookAt(0, 5, 0);
		camera.update();
		
		bossActor.act(delta);
		
		bossAnimator.update(delta);
		
		for(Mob mob : mobs){
			
			mob.time += delta;
			if(mob.time > 5) mob.alive = false;
			
			float speed = 3.5f;
			mob.deltaCam.set(camera.position).sub(mob.position);
			float camDistance = mob.deltaCam.len();
			if(camDistance < 1) mob.alive = false;
			
			mob.deltaCam.scl(1f / camDistance);
			if(mob.time > 1)
			mob.direction.slerp(mob.deltaCam, delta * 5f);
			mob.position.mulAdd(mob.direction, delta * speed);
			
			if(mob.position.len() > 20) mob.alive = false;
			
			mob.model.transform.setToRotation(Vector3.Y, mob.direction);
			mob.model.transform.setTranslation(mob.position);
		}
		
		for(int i=0 ; i<mobs.size ; ){
			Mob mob = mobs.get(i);
			if(mob.alive) i++; else mobs.removeIndex(i);
		}
		
		Gdx.gl.glClearColor(0, 0, 0, 0);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

		batch.begin(camera);
		batch.render(models, env);
		for(Mob mob : mobs){
			batch.render(mob.model, env);
		}
		batch.end();
		
	}
	
	private boolean isRight() {
		return Gdx.input.isKeyPressed(Input.Keys.D); // TODO universal
	}

	private boolean isLeft() {
		return Gdx.input.isKeyPressed(Input.Keys.Q);// TODO universal
	}

	@Override
	public void resize(int width, int height) {
		camera.viewportWidth = width;
		camera.viewportHeight = height;
		camera.update(true);
	}
}
