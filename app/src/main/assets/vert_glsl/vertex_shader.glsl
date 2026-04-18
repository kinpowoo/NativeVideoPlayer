attribute vec4 vPosition;
attribute vec2 vTexCoord;
varying vec2 varTexCoord;
void main() {
    gl_Position = vPosition;
    varTexCoord = vTexCoord; // CPU端翻转后，这里直接透传
}