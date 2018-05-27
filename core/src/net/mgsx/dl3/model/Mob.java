package net.mgsx.dl3.model;

import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.math.Vector3;

public class Mob {
	public ModelInstance model;
	public Vector3 position = new Vector3();
	public Vector3 direction = new Vector3();
	public Vector3 deltaCam = new Vector3();
	public float time;
	public boolean alive = true;
	public int id;
	public boolean light;
	public float energy, energyMax;
	public Material material;
}
