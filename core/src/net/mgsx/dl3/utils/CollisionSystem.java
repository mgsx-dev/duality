package net.mgsx.dl3.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;
import com.badlogic.gdx.utils.ScreenUtils;

public class CollisionSystem 
{
	private Impact impact = new Impact();
	private Environment colEnv;
	private FrameBuffer fboCollisions;
	private ModelBatch batchCollisions;
	
	private Array<ModelInstance> models = new Array<ModelInstance>();
	
	private Pool<Integer> idPool = new Pool<Integer>(){
		private int partsID = 1;
		@Override
		protected Integer newObject() {
			return partsID++;
		}
	};
	
	public CollisionSystem() 
	{
		int width = Gdx.graphics.getBackBufferWidth();
		int height = Gdx.graphics.getBackBufferHeight();
		fboCollisions = new FrameBuffer(Format.RGBA8888, width, height, true);
		
		batchCollisions = new ModelBatch(Gdx.files.internal("shaders/collision.vs"), Gdx.files.internal("shaders/collision.fs"));
		
		colEnv = new Environment();
		colEnv.set(new ColorAttribute(ColorAttribute.Fog, Color.BLUE));
	}
	
	public void addModel(ModelInstance model){
		models.add(model);
	}
	
	public int attachEntity(ModelInstance model)
	{
		int id = idPool.obtain();
		for(Material mat : model.materials){
			setColorCode(mat, id << 8);
		}
		models.add(model);
		return id;
	}
	public int attachEntity(ModelInstance model, String materialID)
	{
		int id = idPool.obtain();
		Material mat = model.getMaterial(materialID);
		setColorCode(mat, id << 8);
		return id;
	}
	public void detachEntity(int id, ModelInstance model)
	{
		idPool.free(id);
		models.removeValue(model, true);
	}
	
	private void setColorCode(Material mat, int colorCode){
		if(!mat.has(ColorAttribute.Emissive)){
			mat.set(new ColorAttribute(ColorAttribute.Emissive, new Color(colorCode)));
		}else{
			mat.get(ColorAttribute.class, ColorAttribute.Emissive).color.set(colorCode);
		}
	}
	
	public Impact update(Camera camera) {
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
		
		impact.occured = false;
		byte[] bytes = ScreenUtils.getFrameBufferPixels(Gdx.input.getX(), Gdx.graphics.getBackBufferHeight() - Gdx.input.getY(), 1, 1, false);
		if(Gdx.input.isTouched()){
			int code = bytes[2] & 0xFF; // ((bytes[0] & 0xFF) << 24) | ((bytes[1] & 0xFF) << 16) | ((bytes[2] & 0xFF) << 8) | (bytes[3] & 0xFF);
			int distance = bytes[3] & 0xFF;
			if(distance > 0){
				impact.distance = MathUtils.lerp(camera.near, camera.far, (distance / 255f));
				impact.occured = true;
				impact.id = code;
			}
			// System.out.println(code);
			// System.out.println()); 
		}
		
		fboCollisions.end();
		
		return impact;
	}
}
