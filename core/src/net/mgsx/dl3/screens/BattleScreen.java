package net.mgsx.dl3.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
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
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ImmediateModeRenderer20;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.scenes.scene2d.Action;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.ScreenUtils;

import net.mgsx.dl3.model.Mob;

public class BattleScreen extends ScreenAdapter
{
	private Camera camera;
	private ModelBatch batch;
	private Array<ModelInstance> models = new Array<ModelInstance>();
	private float time;
	private Vector3 cameraPosition = new Vector3();
	private Environment env, colEnv;
	private Model levelModel;
	private ModelInstance bossModel;
	private AnimationController bossAnimator;
	private Actor bossActor;
	private Array<Mob> mobs = new Array<Mob>();
	private float cameraAngle;
	private float [] beamVertices;
	private ImmediateModeRenderer20 shapeRenderer;
	private Vector3 rayStart = new Vector3();
	private Vector3 rayEnd = new Vector3();
	private Vector3 rayTan = new Vector3();
	private Vector3 rayPos = new Vector3();
	private ShaderProgram beamShader, burstShader;
	private Vector3 intersection = new Vector3();
	
	private FrameBuffer fboCollisions;
	private ModelBatch batchCollisions;
	private boolean pause;
	private boolean touched;
	
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
		
		int width = Gdx.graphics.getBackBufferWidth();
		int height = Gdx.graphics.getBackBufferHeight();
		fboCollisions = new FrameBuffer(Format.RGBA8888, width, height, true);
		
		batchCollisions = new ModelBatch(Gdx.files.internal("shaders/collision.vs"), Gdx.files.internal("shaders/collision.fs"));
		
		colEnv = new Environment();
		colEnv.set(new ColorAttribute(ColorAttribute.Fog, Color.BLUE));
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
	
	// TODO set once
	private void setColorCode(Material mat, int colorCode){
//		ColorAttribute diffuse = mat.get(ColorAttribute.class, ColorAttribute.Diffuse);
		if(!mat.has(ColorAttribute.Emissive)){
			mat.set(new ColorAttribute(ColorAttribute.Emissive, new Color(colorCode)));
		}else{
			mat.get(ColorAttribute.class, ColorAttribute.Emissive).color.set(colorCode);
		}
	}
	private void restoreColors(Material mat){
//		ColorAttribute diffuse = mat.get(ColorAttribute.class, ColorAttribute.Diffuse);
//		ColorAttribute emissive = mat.get(ColorAttribute.class, ColorAttribute.Emissive);
//		diffuse.color.set(emissive.color);
//		emissive.color.set(Color.BLACK);
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
		
		boolean hasImpact = false;
		Ray ray = null; 
		float rayLen = 20f;
		if(touched){
			ray = camera.getPickRay(Gdx.input.getX(), Gdx.input.getY());
			
			// TODO compute collisions
			Mob shootedMob = null;
			for(Mob mob : mobs){
				float mobRadius = .25f;
				if(Intersector.intersectRaySphere(ray, mob.position, mobRadius, intersection)){
					float dst = intersection.dst(ray.origin);
					if(dst < rayLen){
						rayLen = dst;
						shootedMob = mob;
					}
				}
			}
			
			if(shootedMob != null){
				// TODO remove neergy to shootedMob
				shootedMob.alive = false;
				hasImpact = true;
			}
		}
		
		// TODO ScreenUtils.getFrameBufferPixels(x, y, w, h, flipY)
		
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
		
		float lum = .5f;
		Gdx.gl.glClearColor(lum, lum, lum, 0);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

		
		// TODO use a special shader instead (bones and all but use only emissive as color code and give back depth)
		Material matActive = models.first().getMaterial("Active");
		setColorCode(matActive, 512);
		Material matBody = models.first().getMaterial("Boss");
		setColorCode(matBody, 256);
		
		fboCollisions.begin();

		Gdx.gl.glClearColor(0, 0, 0, 0);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
		
		// TODO linked to shader (hard coded)
		camera.near = 1f;
		camera.far = 100f;
		camera.update();
		
		batchCollisions.begin(camera);
		batchCollisions.render(models, colEnv);
		batchCollisions.end();
		
		byte[] bytes = ScreenUtils.getFrameBufferPixels(Gdx.input.getX(), Gdx.graphics.getBackBufferHeight() - Gdx.input.getY(), 1, 1, false);
		if(Gdx.input.isTouched()){
			int code = bytes[2] & 0xFF; // ((bytes[0] & 0xFF) << 24) | ((bytes[1] & 0xFF) << 16) | ((bytes[2] & 0xFF) << 8) | (bytes[3] & 0xFF);
			int fog = bytes[3] & 0xFF;
			if(fog > 0){
				float frustrumDistance = MathUtils.lerp(camera.near, camera.far, (fog / 255f));
				rayLen = frustrumDistance;
				hasImpact = true;
			}
			System.out.println(code);
			// System.out.println()); 
		}
		
		fboCollisions.end();
		
		// restore emissive
		restoreColors(matActive);
		restoreColors(matBody);
		
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
			
			
			if(hasImpact){
				
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
