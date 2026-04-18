precision mediump float;
uniform sampler2D uTexture;
varying vec2 varTexCoord;

void main() {

    // 模糊半径 (Radius)：这个值越大，文字消失得越彻底
    // 这个值必须是高清分辨率下（例如全屏）的步长
    float blurRadius = 30.0;

    vec4 color = vec4(0.0);
    float totalWeight = 0.0;

    // 简单的 9x9 方框模糊，采样范围极大
    for (float i = -2.0; i <= 10.0; i += 1.0) {
        for (float j = -2.0; j <= 6.0; j += 1.0) {
            vec2 offset = vec2(i, j) * blurRadius * uTexelOffset;
            color += texture2D(uTexture, varTexCoord + offset);
            totalWeight += 1.0;
        }
    }

    gl_FragColor = vec4(color.rgb / totalWeight, 1.0);
}