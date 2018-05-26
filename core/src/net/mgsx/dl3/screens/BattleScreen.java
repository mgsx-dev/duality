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
import com.badlogic.gdx.graphics.glutils.ImmediateModeRenderer20;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.scenes.scene2d.Action;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.JsonReader;

import net.mgsx.dl3.model.EnemyPart;
import net.mgsx.dl3.model.Mob;
import net.mgsx.dl3.utils.CollisionSystem;
import net.mgsx.dl3.utils.Impact;

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
	private ImmediateModeRenderer20 shapeRenderer;
	private Vector3 rayStart = new Vector3();
	private Vector3 rayEnd = new Vector3();
	private Vector3 rayTan = new Vector3();
	private Vector3 rayPos = new Vector3();
	private ShaderProgram beamShader, burstShader;
	
	private ColorAttribute lastColor;
	private Color colorBackup = new Color();
	
	private boolean pause;
	private boolean touched;
	
	private Array<EnemyPart> detachedParts = new Array<EnemyPart>();
	
	private CollisionSystem collisionSystem;
	private int bossID;
	
	private EnemyPart emit1Part;
	
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
		
		beamShader = new ShaderProgram(Gdx.files.internal("shaders/beam.vs"), Gdx.files.internal("shaders/beam.fs"));
		if(!beamShader.isCompiled()) throw new GdxRuntimeException(beamShader.getLog());
		
		burstShader = new ShaderProgram(Gdx.files.internal("shaders/burst.vs"), Gdx.files.internal("shaders/burst.fs"));
		if(!burstShader.isCompiled()) throw new GdxRuntimeException(burstShader.getLog());
		
		shapeRenderer = new ImmediateModeRenderer20(4, false, true, 1, beamShader);
		
		collisionSystem = new CollisionSystem();
		
		collisionSystem.addModel(bossModel);
		bossID = collisionSystem.attachEntity(bossModel, "Boss");
		
		emit1Part = new EnemyPart();
		emit1Part.model = bossModel;
		emit1Part.node = bossModel.getNode("Emit1", true);
		emit1Part.material = bossModel.getMaterial("Active");
		emit1Part.id = collisionSystem.attachEntity(bossModel, "Active");
		emit1Part.energy = emit1Part.energyMax = 1; // energy in seconds of beam
	}
	
	private Action emit(final String emitterID, final String emittedID) 
	{
		return Actions.run(new Runnable() {
			@Override
			public void run() {
				Node emitter = bossModel.getNode(emitterID, true);
				if(emitter == null) return; // case detached
				ModelInstance model = new ModelInstance(levelModel, emittedID);
				model.nodes.first().translation.setZero();
				model.calculateTransforms();
				Mob mob = new Mob();
				mob.model = model;
				mob.position.setZero().mul(emitter.globalTransform);
				mob.direction.set(Vector3.Y).rot(emitter.globalTransform);
				mob.id = collisionSystem.attachEntity(model);
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
		if(Gdx.input.isKeyJustPressed(Input.Keys.SPACE)){
			pause = !pause;
		}
		if(pause){
			return;
		}
		touched = Gdx.input.isTouched();
		
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
		
		Ray ray = null; 
		float rayLen = 20f;
		if(touched){
			ray = camera.getPickRay(Gdx.input.getX(), Gdx.input.getY());
//			
//			// TODO compute collisions
//			Mob shootedMob = null;
//			for(Mob mob : mobs){
//				float mobRadius = .25f;
//				if(Intersector.intersectRaySphere(ray, mob.position, mobRadius, intersection)){
//					float dst = intersection.dst(ray.origin);
//					if(dst < rayLen){
//						rayLen = dst;
//						shootedMob = mob;
//					}
//				}
//			}
//			
//			if(shootedMob != null){
//				// TODO remove neergy to shootedMob
//				shootedMob.alive = false;
//				hasImpact = true;
//			}
		}
		
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
			if(mob.alive) i++; else{
				mobs.removeIndex(i);
				collisionSystem.detachEntity(mob.id, mob.model);
			}
		}
		
		float lum = .5f;
		Gdx.gl.glClearColor(lum, lum, lum, 0);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

		if(lastColor != null){
			lastColor.color.set(colorBackup);
			lastColor = null;
		}
		
		Impact impact = collisionSystem.update(camera);
		if(impact.occured){
			rayLen = impact.distance;
			
			
			for(Mob mob : mobs){
				if(mob.id == impact.id){
					mob.alive = false; // TODO energy
				}
			}
			if(impact.id == bossID){
				// nothing to do
			}else if(impact.id == emit1Part.id){
				// TODO flash part
				emit1Part.energy -= delta;
				
				if(emit1Part.energy <= 0){
					emit1Part.material.get(ColorAttribute.class, ColorAttribute.Diffuse).color.set(Color.BLACK);
					// emit1Part.node.detach();
					emit1Part.node.isAnimated = false;
					detachedParts.add(emit1Part);
				}else{
					lastColor = emit1Part.material.get(ColorAttribute.class, ColorAttribute.Diffuse);
				}
			}
		}
		
		if(lastColor != null){
			colorBackup.set(lastColor.color);
			float freq = 20;
			if((time * freq) % 2f > 1)
				lastColor.color.set(Color.WHITE);
		}
		
		
		// update detached parts
		for(int i=0 ; i<detachedParts.size ; )
		{
			EnemyPart part = detachedParts.get(i);
			if(part.direction == null){
				part.direction = new Vector3(part.node.translation).nor();
			}
			part.node.translation.mulAdd(part.direction, delta * 1f);
			part.node.translation.y -= part.time * .003f;
			part.node.isAnimated = false;
			part.time += delta;
			if(part.node.translation.y < 0 || part.time > 30){
				emit1Part.node.detach();
				// part.model.nodes.removeValue(part.node, true);
				detachedParts.removeIndex(i);
			}else{
				i++;
			}
		}
		
		
		batch.begin(camera);
		batch.render(models, env);
		for(Mob mob : mobs){
			batch.render(mob.model, env);
		}
		batch.end();
		
		if(ray != null){
			
			ray.getEndPoint(rayEnd, rayLen);
			
			rayTan.set(camera.direction).crs(camera.up).nor();
			rayStart.set(camera.position);
			
			float rayBias = 1f;
			//rayStart.mulAdd(rayTan, rayBias);
			// rayStart.mulAdd(camera.up, -rayBias);
			rayStart.y -= 1f;
			
			Gdx.gl.glEnable(GL20.GL_BLEND);
			Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
			
			beamShader.begin();
			beamShader.setUniformf("u_time", time);
			
			shapeRenderer.begin(camera.combined, GL20.GL_TRIANGLE_STRIP);
			shapeRenderer.setShader(beamShader);
			float rayWidth = 1f;
			
			rayPos.set(rayStart).mulAdd(rayTan, rayWidth);
			shapeRenderer.color(Color.WHITE);
			shapeRenderer.texCoord(0, 0);
			shapeRenderer.vertex(rayPos.x, rayPos.y, rayPos.z);
			
			rayPos.set(rayStart).mulAdd(rayTan, -rayWidth);
			shapeRenderer.color(Color.WHITE);
			shapeRenderer.texCoord(1, 0);
			shapeRenderer.vertex(rayPos.x, rayPos.y, rayPos.z);
			
			rayPos.set(rayEnd).mulAdd(rayTan, rayWidth);
			shapeRenderer.color(Color.WHITE);
			shapeRenderer.texCoord(0, rayLen);
			shapeRenderer.vertex(rayPos.x, rayPos.y, rayPos.z);
			
			rayPos.set(rayEnd).mulAdd(rayTan, -rayWidth);
			shapeRenderer.color(Color.WHITE);
			shapeRenderer.texCoord(1, rayLen);
			shapeRenderer.vertex(rayPos.x, rayPos.y, rayPos.z);
			
			
			shapeRenderer.end();
			
			
			if(impact.occured){
				
				float impactSize = 3f;
				shapeRenderer.begin(camera.combined, GL20.GL_TRIANGLE_STRIP);
				shapeRenderer.setShader(burstShader);
				
				rayPos.set(rayEnd).mulAdd(rayTan, impactSize).mulAdd(camera.up, impactSize);
				shapeRenderer.color(Color.WHITE);
				shapeRenderer.texCoord(0, 0);
				shapeRenderer.vertex(rayPos.x, rayPos.y, rayPos.z);
				
				rayPos.set(rayEnd).mulAdd(rayTan, -impactSize).mulAdd(camera.up, impactSize);
				shapeRenderer.color(Color.WHITE);
				shapeRenderer.texCoord(1, 0);
				shapeRenderer.vertex(rayPos.x, rayPos.y, rayPos.z);
				
				rayPos.set(rayEnd).mulAdd(rayTan, impactSize).mulAdd(camera.up, -impactSize);
				shapeRenderer.color(Color.WHITE);
				shapeRenderer.texCoord(0, 1);
				shapeRenderer.vertex(rayPos.x, rayPos.y, rayPos.z);
				
				rayPos.set(rayEnd).mulAdd(rayTan, -impactSize).mulAdd(camera.up, -impactSize);
				shapeRenderer.color(Color.WHITE);
				shapeRenderer.texCoord(1, 1);
				shapeRenderer.vertex(rayPos.x, rayPos.y, rayPos.z);
				
				
				
				shapeRenderer.end();
			}
			
		}
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
