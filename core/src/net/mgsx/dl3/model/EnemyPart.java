package net.mgsx.dl3.model;

import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.math.Vector3;

public class EnemyPart {
	public ModelInstance model;
	public Material material;
	public Node node;
	public int id;
	public float energy;
	public float energyMax;
	public float time;
	public Vector3 direction;
	public boolean light;
}
