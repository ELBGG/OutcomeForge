#version 150
// The game's render output
uniform sampler2D DiffuseSampler;
// The texture coordinate represented as a 2D vector (x,y)
in vec2 texCoord;
// The output color of each pixel represented as a 4D vector (r,g,b,a)
out vec4 fragColor;

// Umbral para determinar blanco o negro (0.0 - 1.0)
const float threshold = 0.5;

void main() {
    // Extract the original color of the pixel from the DiffuseSampler
    vec4 original = texture(DiffuseSampler, texCoord);

    // Calcular luminancia (brillo percibido del píxel)
    float luminance = dot(original.rgb, vec3(0.299, 0.587, 0.114));

    // Convertir a blanco o negro puro basado en el umbral
    // Si luminance >= threshold -> blanco (1.0), sino -> negro (0.0)
    float bw = step(threshold, luminance);

    // Aplicar el color blanco o negro, manteniendo el alpha original
    vec4 result = vec4(vec3(bw), original.a);

    // Set the fragColor output as the result
    fragColor = result;
}