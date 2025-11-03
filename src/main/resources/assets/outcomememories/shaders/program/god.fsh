#version 150
uniform sampler2D DiffuseSampler;
uniform float u_time;           // tiempo en segundos (animación)
uniform vec2 u_resolution;      // resolución del render target (px)

in vec2 texCoord;
out vec4 fragColor;

const float toonLevels = 4.0;

// Hash / noise helpers
float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 345.45));
    p += dot(p, p + 34.345);
    return fract(p.x * p.y);
}

vec2 hash22(vec2 p) {
    float h = hash21(p);
    return fract(vec2(h, hash21(p + h + 1.234)));
}

// rotated coordinates helper
mat2 rot(float a) {
    float c = cos(a), s = sin(a);
    return mat2(c, -s, s, c);
}

/*
  shardLayer:
  - genera formas "erráticas" tipo fragmentos/triángulos/diamante
  - scale controla tamaño de fragmentos
  - thresh controla agresividad (mayor -> menos cobertura)
  - time introduce animación (rotaciones y jitter)
*/
float shardLayer(vec2 uv, float scale, float thresh, float time, float seed) {
    // coordenadas escaladas y jitteradas por celda
    vec2 p = uv * scale;
    vec2 ip = floor(p);
    vec2 fp = fract(p) - 0.5;

    // offset por celda pseudo-aleatorio
    vec2 rnd = hash22(ip + seed);
    float ang = (rnd.x - 0.5) * 3.14 + time * (0.2 + 0.05 * rnd.y);
    vec2 centerOffset = (rnd - 0.5) * 0.6;

    // rotar respecto al centro de la celda para crear "arcos"
    vec2 q = rot(ang) * (fp - centerOffset);

    // usar función tipo "diamond / triangle" (max de absolutos) para formas triangulares
    float d = max(abs(q.x) * (0.8 + 0.4 * rnd.y), abs(q.y) * (0.6 + 0.6 * rnd.x));

    // un pequeño ruido interno para romper bordes
    float edgeNoise = hash21(ip + seed * 7.1) * 0.3;
    float mask = smoothstep(0.5 + edgeNoise - thresh, 0.0 + edgeNoise, d);

    // añadir líneas estriadas internas con función fractal para aspecto "agrietado"
    float stripe = abs(fract((fp.x + fp.y * 0.3 + time * 0.5) * 4.0) - 0.5);
    mask *= smoothstep(0.15, 0.0, stripe * (1.0 + rnd.x * 0.8));

    return mask;
}

void main() {
    // Normalizar coords (centro + aspect)
    vec2 uv = texCoord;
    // obtener color base (lo usamos solo para luminancia), pero priorizamos rojo en el shader
    vec4 src = texture(DiffuseSampler, uv);
    float lum = dot(src.rgb, vec3(0.2126, 0.7152, 0.0722));

    // Generar varias capas de shards con distintos escalados y semillas
    float t = u_time * 0.8;

    float layer1 = shardLayer(uv, 8.0, 0.20, t * 0.8, 1.0);
    float layer2 = shardLayer(uv, 18.0, 0.25, t * 1.1, 2.0);
    float layer3 = shardLayer(uv, 34.0, 0.35, t * 1.8, 3.0);
    float layer4 = shardLayer(uv + vec2(sin(t*0.3)*0.02, cos(t*0.27)*0.02), 64.0, 0.5, t * 2.5, 4.0);

    // Componer máscara final de fragmentos (más capas -> apariencia más densa / errática)
    float shards = clamp(max(max(layer1, layer2), max(layer3, layer4)), 0.0, 1.0);

    // Crear "núcleos" brillantes en algunas fragmentos (amarillos / blancos)
    // Usamos hash para decidir dónde aparecen highlights
    float highlightSeed = hash21(uv * 200.0 + floor(u_time * 0.5));
    float highlight = smoothstep(0.6, 0.99, shards) * step(0.85, highlightSeed);

    // Generar color base predominantemente rojo
    // baseRed modulado por luminancia de la textura (permite integrar algo del albedo)
    vec3 baseRed = vec3(1.0, 0.05, 0.02) * (0.5 + lum * 0.8);

    // color para shards: mezcla entre rojo intenso y tonos amarillos para highlights
    vec3 shardColor = mix(vec3(0.6, 0.0, 0.0), vec3(1.0, 0.9, 0.05), highlight);

    // añadir variación aleatoria de tono dentro de shards (pequeña oscilación hacia magenta/black)
    float toneVar = hash21(uv * 50.0 + floor(u_time * 0.7)) * 0.5;
    shardColor *= (0.8 + 0.4 * toneVar);

    // aplicar máscara principal: donde shards==1 mostramos shardColor, en otros casos baseRed
    vec3 combined = mix(baseRed, shardColor, shards);

    // introducir "hue cracks" negativos (agujeros negros irregulares)
    // invertimos en zonas de alto contraste de otra capa de noise
    float holeNoise = hash21(uv * 120.0 + 7.7);
    float holeMask = smoothstep(0.88, 0.995, shards * (0.4 + holeNoise * 0.6));
    combined = mix(combined, vec3(0.02, 0.0, 0.0), holeMask);

    // Añadir un poco de brillo general y un bloom simulado (saturación en rojos)
    float brightness = 0.3 * shards + 0.2 * highlight;
    combined += vec3(brightness * 0.6, brightness * 0.15, brightness * 0.05);

    // Toon quantization (cuantizamos la intensidad para ese look tipo "posterizado")
    vec3 quant = floor(combined * toonLevels) / toonLevels;

    // Añadir un ligero vignette/gradiente para reforzar contraste
    vec2 pos = uv - 0.5;
    pos.x *= u_resolution.x / max(1.0, u_resolution.y);
    float vign = smoothstep(0.8, 0.2, length(pos) * 1.2);
    quant *= vign;

    // Añadir transparencia basada en original alpha si se desea (por defecto 1)
    float alpha = src.a;

    fragColor = vec4(quant, alpha);
}