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

abstract public class BattleScreen extends ScreenAdapter
{
	public static final float MOB_ENERGY = .5f; // energy in seconds of beam
	public static final float PART_ENERGY = 1f; // energy in seconds of beam
	public static final float BOSS_ENERGY = 10f; // energy in seconds of beam
	
	private Camera camera;
	private ModelBatch batch;
	protected Array<ModelInstance> models = new Array<ModelInstance>();
	private float time;
	private Vector3 cameraPosition = new Vector3();
	private Environment env;
	protected Model levelModel;
	protected ModelInstance bossModel;
	protected AnimationController bossAnimator;
	protected Actor bossActor;
	private Array<Mob> mobs = new Array<Mob>();
	private float cameraAngle;
	private ImmediateModeRenderer20 shapeRenderer;
	private Vector3 rayStart = new Vector3();
	private Vector3 rayEnd = new Vector3();
	private Vector3 rayTan = new Vector3();
	private Vector3 rayPos = new Vector3();
	private ShaderProgram beamShaderLight, beamShaderDark, burstShaderLight, burstShaderDark;
	
	private ColorAttribute lastColor;
	private Color colorBackup = new Color();
	
	private boolean pause;
	private boolean touched;
	
	protected float bossLife = 1;
	
	private Array<EnemyPart> detachedParts = new Array<EnemyPart>();
	
	protected CollisionSystem collisionSystem;
	private int bossID;
	
	protected Array<EnemyPart> enemyParts = new Array<EnemyPart>();
	private DirectionalLight directionalLight;
	private ColorAttribute ambientLight;
	
	public BattleScreen(String modelFile) {
		camera = new PerspectiveCamera(60f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		camera.position.set(2, 2, 2);
		camera.lookAt(Vector3.Zero);
		camera.update();
		batch = new ModelBatch();
		
		levelModel = new G3dModelLoader(new JsonReader()).loadModel(Gdx.files.internal(modelFile));
		
//		Model model = new ModelBuilder()
//		.createBox(1f, 1f, 1f, 
//				new Material(new ColorAttribute(ColorAttribute.Diffuse, Color.WHITE)), 
//				new VertexAttributes(VertexAttribute.Position()).getMask());
		models.add(bossModel = new ModelInstance(levelModel, "Boss", "BossMesh"));
		
		bossAnimator = new AnimationController(bossModel);
		
		bossActor = new Actor();
		
		env = new Environment();
		env.add(directionalLight = new DirectionalLight().set(Color.WHITE, new Vector3(1, -5, 1).nor()));
		env.set(ambientLight = new ColorAttribute(ColorAttribute.AmbientLight, new Color(Color.WHITE).mul(.4f)));
		
		beamShaderLight = new ShaderProgram(Gdx.files.internal("shaders/beam.vs"), Gdx.files.internal("shaders/beam.fs"));
		if(!beamShaderLight.isCompiled()) throw new GdxRuntimeException(beamShaderLight.getLog());
		
		beamShaderDark = new ShaderProgram(Gdx.files.internal("shaders/beam.vs"), Gdx.files.internal("shaders/beam-dark.fs"));
		if(!beamShaderDark.isCompiled()) throw new GdxRuntimeException(beamShaderDark.getLog());
		
		burstShaderLight = new ShaderProgram(Gdx.files.internal("shaders/burst.vs"), Gdx.files.internal("shaders/burst.fs"));
		if(!burstShaderLight.isCompiled()) throw new GdxRuntimeException(burstShaderLight.getLog());
		
		burstShaderDark = new ShaderProgram(Gdx.files.internal("shaders/burst.vs"), Gdx.files.internal("shaders/burst-dark.fs"));
		if(!burstShaderDark.isCompiled()) throw new GdxRuntimeException(burstShaderDark.getLog());
		
		shapeRenderer = new ImmediateModeRenderer20(4, false, true, 1, beamShaderLight);
		
		collisionSystem = new CollisionSystem();
		
		collisionSystem.addModel(bossModel);
		bossID = collisionSystem.attachEntity(bossModel, "Boss");
	}
	
	protected Action emit(final String emitterID, final String emittedID, final boolean light) 
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
				mob.light = light;
				mob.energyMax = mob.energy = MOB_ENERGY;
				mob.material = model.getMaterial("Boss");
				mobs.add(mob);
			}
		});
	}

	protected Action animate(final AnimationController animator, String nodeNameID, String animationID, final float speed){
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
		
		boolean lightRay = Gdx.input.isButtonPressed(Input.Buttons.LEFT);
		
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
		cameraPosition.y = 2;
		
		camera.position.set(cameraPosition);
		camera.up.set(Vector3.Y);
		camera.lookAt(0, 4, 0);
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
		
		directionalLight.color.set(Color.WHITE).mul(bossLife);
		ambientLight.color.set(Color.WHITE).mul(1.0f - bossLife);
		
		float lum = bossLife;
		Gdx.gl.glClearColor(lum, lum * 1.1f, lum * 1.2f, 0);
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
					if(lightRay != mob.light){
						mob.energy -= delta;
						mob.alive = mob.energy > 0;
						lastColor = mob.material.get(ColorAttribute.class, ColorAttribute.Diffuse);
					}
				}
			}
			if(impact.id == bossID){
				// nothing to do
			}else{
				
				for(EnemyPart enemyPart : enemyParts){
					if(impact.id == enemyPart.id){
						
						if(enemyPart.light == null){
							// internal boss : TODO take energy global boss
							bossLife = MathUtils.clamp(bossLife + (lightRay ? delta : -delta) / BOSS_ENERGY, 0, 1);
							lastColor = enemyPart.material.get(ColorAttribute.class, ColorAttribute.Diffuse);
						}
						else if(lightRay != enemyPart.light){
							
							// TODO flash part
							enemyPart.energy -= delta;
							
							if(enemyPart.energy <= 0){
								enemyPart.material.get(ColorAttribute.class, ColorAttribute.Diffuse).color.set(Color.BLACK);
								// emit1Part.node.detach();
								if(enemyPart.node != null){
									enemyPart.node.isAnimated = false;
								}
								detachedParts.add(enemyPart);
							}else{
								lastColor = enemyPart.material.get(ColorAttribute.class, ColorAttribute.Diffuse);
							}
						}else{
							// TODO give energy back
						}
						
					}
				}
				
			}
		}
		
		if(lastColor != null){
			colorBackup.set(lastColor.color);
			float freq = 20;
			if((time * freq) % 2f > 1)
				lastColor.color.set(Color.BLACK);
		}
		
		
		// update detached parts
		for(int i=0 ; i<detachedParts.size ; )
		{
			EnemyPart part = detachedParts.get(i);
			if(part.node != null){
				if(part.direction == null){
					part.direction = new Vector3(part.node.translation).nor();
				}
				part.node.translation.mulAdd(part.direction, delta * 1f);
				part.node.translation.y -= part.time * .003f;
				part.node.isAnimated = false;
				part.time += delta;
				if(part.node.translation.y < 0 || part.time > 30){
					part.node.detach();
					// part.model.nodes.removeValue(part.node, true);
					detachedParts.removeIndex(i);
					continue;
				}
			}
			i++;
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
			
			if(lightRay)
				Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
			else
				Gdx.gl.glBlendFuncSeparate(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, GL20.GL_SRC_ALPHA, GL20.GL_ONE);
			
			ShaderProgram beamShader = lightRay ? beamShaderLight : beamShaderDark;
			
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
				
				ShaderProgram burstShader =  lightRay ? burstShaderLight : burstShaderDark;
				
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
