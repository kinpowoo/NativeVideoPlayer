precision mediump float;
uniform sampler2D uTexture;
varying vec2 varTexCoord;

void main() {
    // 1. 直接采样（由 FBO 的 12x12 和 GL_LINEAR 负责平滑度）
    vec4 color = texture2D(uTexture, varTexCoord);

    // 2. 亮度提取
    float luma = dot(color.rgb, vec3(0.299, 0.587, 0.114));

    // 3. --- 核心：仿图片效果的“提亮去黑”算法 ---
    // 无论原图多黑，我们都强制给它一个极高的“基底亮度”
    // 这个 baseWhite 决定了你截图中那种白蒙蒙的质感
    vec3 baseWhite = vec3(0.92, 0.92, 0.94);

    // 线性混合：原色越暗，白色补偿越多
    // 这里的 0.8 是提亮强度，1.0 则是全白
    vec3 resColor = mix(baseWhite, color.rgb, luma * 0.8 + 0.2);

    // 4. --- 降低饱和度 ---
    // 让颜色看起来更清冷、不刺眼
    float saturation = 0.35;
    vec3 gray = vec3(luma);
    vec3 finalColor = mix(gray, resColor, saturation);

    // 5. 再次全局提亮，确保整体视觉是“轻盈”的
    gl_FragColor = vec4(color.rgb, 1.0);
}