precision mediump float;
uniform sampler2D uTexture;
varying vec2 varTexCoord;

void main() {
    //vec4 color = texture2D(uTexture, varTexCoord);
    // 计算亮度
    //float luma = dot(color.rgb, vec3(0.299, 0.587, 0.114));

    vec4 color = texture2D(uTexture, varTexCoord);
    // 提亮：把暗部往上拉，确保背景清冷温润
    vec3 lightened = mix(vec3(0.95), color.rgb, 0.9);
    // 降低饱和度
    // 计算亮度
    float luma = dot(lightened, vec3(0.299, 0.587, 0.114));
    vec4 blurColor = vec4(mix(vec3(luma), lightened, 0.5), 1.0);
    //gl_FragColor = blurColor;  //在这里不作下面的处理，就是正常封面颜色，黑色不会变成白色

    // 如果很黑 (darkness大)，就强制转成 milkWhite
    // 使用 pow 让中间亮色部分保持原样，暗部才漂白，2.0中等漂白，值越大越会保持原色彩
    float shiftFactor = pow(darkness, 1.5);
    vec3 finalBlurRGB = mix(blurColor.rgb, color.rgb, shiftFactor);

    // 5. 最终混合
    // 边缘 10 像素显示原图 (color)，中间显示漂白后的模糊图 (finalBlurRGB)
    gl_FragColor = vec4(finalBlurRGB, 1.0);
}