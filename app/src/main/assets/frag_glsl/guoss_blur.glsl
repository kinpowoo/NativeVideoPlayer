precision mediump float;
uniform sampler2D uTexture;
varying vec2 varTexCoord;

// 高斯模糊函数
vec4 gaussianBlur(vec2 texCoord) {
    vec4 sum = vec4(0.0);
    float total = 0.0;
    float radius = 5.0;
    vec2 u_TextureSize = vec2(16.0,16.0)

    for(float x = -radius; x <= radius; x += 1.0) {
        for(float y = -radius; y <= radius; y += 1.0) {
            float weight = exp(-(x*x + y*y) / (2.0 * radius * radius));
            vec2 offset = vec2(x, y) / u_TextureSize;
            sum += texture2D(uTexture, texCoord + offset) * weight;
            total += weight;
        }
    }

    return sum / total;
}

// RGB转HSV
vec3 rgb2hsv(vec3 c) {
    vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
    vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
    vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));

    float d = q.x - min(q.w, q.y);
    float e = 1.0e-10;
    return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
}

// HSV转RGB
vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

void main() {
    // 1. 获取模糊后的颜色
    vec4 blurredColor = gaussianBlur(varTexCoord);

    // 2. 调整颜色
    vec3 hsv = rgb2hsv(blurredColor.rgb);

    float u_Saturation = 0.85;
    // 调整饱和度
    hsv.y *= u_Saturation;

    // 2. 亮度提取
    float u_Brightness = 0.9;
    // 调整亮度
    hsv.z *= u_Brightness;

    // 转换回RGB
    vec3 finalColor = hsv2rgb(hsv);

    // 3. 输出（可设置透明度）
    gl_FragColor = vec4(finalColor, 1.0);
}