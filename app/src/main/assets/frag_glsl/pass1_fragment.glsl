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
    vec4 blurColor = vec4(mix(vec3(luma), lightened, 0.5), 1.0);

    // 4. 强制漂白逻辑 (针对黑色封面)
    // 提取模糊后的“黑暗程度”
    float darkness = 1.0 - max(max(blurColor.r, blurColor.g), blurColor.b);
    vec3 milkWhite = vec3(0.96, 0.96, 0.94);

    // 如果很黑 (darkness大)，就强制转成 milkWhite
    // 使用 pow 让中间亮色部分保持原样，暗部才漂白
    float shiftFactor = pow(darkness, 3.0);
    vec3 finalBlurRGB = mix(blurColor.rgb, milkWhite, shiftFactor);

    // 5. 最终混合
    // 边缘 10 像素显示原图 (color)，中间显示漂白后的模糊图 (finalBlurRGB)
    gl_FragColor = vec4(finalBlurRGB, 1.0);
}