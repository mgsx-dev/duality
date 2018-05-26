#ifdef GL_ES 
#define LOWP lowp
#define MED mediump
#define HIGH highp
precision mediump float;
#else
#define MED
#define LOWP
#define HIGH
#endif

uniform vec4 u_emissiveColor;
varying vec4 v_position;
varying float v_fog;
varying float v_distance;

void main() {
//	float dist_lsb = v_distance / 256.0;
//	float dist_msb = v_distance / 256.0;
	
	gl_FragColor = vec4(u_emissiveColor.rgb, v_distance);
}
