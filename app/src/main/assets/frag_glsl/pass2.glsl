precision mediump float;
uniform sampler2D uTexture;
uniform vec2 uTexelOffset;
varying vec2 varTexCoord;

void main() {
    // 1. 获取基础坐标和原色
    vec2 uv = varTexCoord;
    vec4 oriColor = texture2D(uTexture, uv);

    // 2. 定义边缘保护范围（像素单位）
    float margin = 10.0;
    float feather = 5.0; // 模糊淡入的过渡带宽（例如 5 像素内完成过渡）

    // 将 UV 坐标转为像素坐标
    vec2 pixelCoords = uv / uTexelOffset;
    vec2 textureSize = 1.0 / uTexelOffset;

    // 3. 计算四个边的平滑权重
    // smoothstep 在 margin-feather 到 margin 之间从 0 变到 1
    float left = smoothstep(margin - feather, margin, pixelCoords.x);
    float right = smoothstep(margin - feather, margin, textureSize.x - pixelCoords.x);
    float top = smoothstep(margin - feather, margin, pixelCoords.y);
    float bottom = smoothstep(margin - feather, margin, textureSize.y - pixelCoords.y);

    // 综合四个边的权重，得到最终的混合系数 (0.0 为完全原色，1.0 为完全模糊)
    float mask = left * right * top * bottom;

    // 4. 执行你之前的模糊算法
    float blurRadius = 30.0;
    vec4 blurColor = vec4(0.0);
    float totalWeight = 0.0;

    // 性能小提示：如果 mask 为 0（完全在边缘），可以跳过循环优化性能
    if (mask > 0.0) {
        for (float i = -2.0; i <= 10.0; i += 2.0) {
            for (float j = -2.0; j <= 6.0; j += 2.0) {
                vec2 offset = vec2(i, j) * blurRadius * uTexelOffset;
                blurColor += texture2D(uTexture, uv + offset);
                totalWeight += 1.0;
            }
        }
        blurColor /= totalWeight;
    } else {
        blurColor = oriColor;
    }

    // 5. 按照平滑权重进行混合
    vec4 blurredColor = mix(oriColor, blurColor, mask);
    gl_FragColor = blurredColor;
}