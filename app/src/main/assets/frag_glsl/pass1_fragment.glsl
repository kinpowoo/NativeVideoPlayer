precision mediump float;
uniform sampler2D uTexture;
varying vec2 varTexCoord;

void main() {
    //vec4 color = texture2D(uTexture, varTexCoord);

    // 计算亮度
    //float luminance = dot(color.rgb, vec3(0.299, 0.587, 0.114));

    vec4 color = texture2D(uTexture, varTexCoord);
    // 提亮：把暗部往上拉，确保背景清冷温润
    vec3 lightened = mix(vec3(0.95), color.rgb, 0.9);
    // 降低饱和度
    float luma = dot(lightened, vec3(0.299, 0.587, 0.114));
    gl_FragColor = vec4(mix(vec3(luma), lightened, 0.5), 1.0);
}