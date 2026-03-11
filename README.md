# DSM Occlusion (Java CPU)

Java-17-Implementierung eines CPU-basierten DSM-Occlusion-Werkzeugs nach dem Vorbild von `sitn/DSM-Occlusion`.

## Eigenschaften

- Liest lokale GeoTIFFs und Remote-COGs per GeoTools/ImageIO-Ext.
- Verarbeitet nur die angefragte BBOX und liest Rasterdaten etappenweise pro Tile.
- Nutzt gepufferte Tiles, schneidet nach der Berechnung wieder auf das Kern-Tile zurück und schreibt nur Kern-Tiles mit Daten.
- Parallele Tile-Verarbeitung über mehrere CPU-Kerne.
- Deterministische Zufallszahlen mit festem Seed.
- Tiled-Output ist Standard; Einzel-GeoTIFF ist optional.
- Strukturierte Laufzeit-Logs mit optionalem `--verbose` für Detailmeldungen.
- Zwei Algorithmen: exakter CPU-Raytracer (`--algorithm exact`) und horizonbasierte Approximation (`--algorithm horizon`).

## Voraussetzungen

- Java 17
- Netzwerkzugriff für Remote-COGs
- Eingaberasters im projizierten CRS mit Meter-Einheiten
- Nordorientierter GeoTIFF ohne Rotation/Shear
- Single-Band-DSM

## Build

```bash
./gradlew test
./gradlew run --args="--help"
```

## Nutzung

Standardmodus: tiled output in `output_tiles`.

```bash
./gradlew run --args="\
  -i https://example.org/dsm.tif \
  --bbox 2590000,1210000,2592000,1212000 \
  -t 512 \
  --bufferMeters 25 \
  -r 128 \
  --threads 8"
```

Einzeldatei:

```bash
./gradlew run --args="\
  -i https://example.org/dsm.tif \
  --bbox 2590000,1210000,2592000,1212000 \
  --singleFile \
  -o result.tif \
  -t 512 \
  --bufferMeters 25"
```

Resume ab Tile 10:

```bash
./gradlew run --args="\
  -i https://example.org/dsm.tif \
  --bbox 2590000,1210000,2592000,1212000 \
  --outputDir output_tiles \
  --startTile 10"
```

## Optionen

| Option | Standard | Beschreibung |
| --- | --- | --- |
| `-i` | – | Eingabe-URL oder lokaler Pfad |
| `--bbox` | – | BBOX im Raster-CRS: `minX,minY,maxX,maxY` |
| `--algorithm` | `exact` | `exact` für Raytracing, `horizon` für schnelle Horizont-Approximation |
| `-o` | `output.tif` | Ausgabe im `--singleFile`-Modus |
| `--outputDir` | `output_tiles` | Zielverzeichnis für Tiled-Output |
| `-r` | `1024` | Rays pro Pixel |
| `-t` | `2000` | Tile-Grösse in Pixeln |
| `-b` | – | Buffer in Pixeln |
| `--bufferMeters` | – | Buffer in Metern |
| `-e` | `1.0` | Vertikale Überhöhung |
| `-B` | `0` | Maximale Anzahl Bounces |
| `--bias` | `1.0` | Bias der Ray-Verteilung |
| `--ambientPower` | `0` | Additiver Ambient-Anteil pro gültigem Ausgabepixel |
| `--skyPower` | `1` | Gleichmässige Himmelshelligkeit |
| `--sunPower` | `0` | Sonnenleistung |
| `--sunAzimuth` | `0` | Azimut im Uhrzeigersinn ab Norden |
| `--sunElevation` | `45` | Sonnenhöhe über dem Horizont |
| `--sunAngularDiam` | `11.4` | Winkeldurchmesser der Sonne |
| `--horizonDirections` | `32` | Nur für `--algorithm horizon`: Anzahl Azimut-Richtungen |
| `--horizonRadiusMeters` | – | Nur für `--algorithm horizon`: maximale Horizont-Suchdistanz in Metern |
| `--singleFile` | `false` | Schreibt ein GeoTIFF für die gesamte angefragte BBOX |
| `--tiled` | `false` | Kompatibilitätsalias; tiled ist bereits Default |
| `--startTile` | `0` | Überspringt frühere Tiles |
| `--outputByte` | `false` | Nur für tiled: clamp `[0,1]` nach Byte `[0,255]` |
| `--threads` | `availableProcessors()` | Gesamt-Threadbudget; bei `exact` auf Tile-Worker und Tile-Compute aufgeteilt, bei `horizon` Anzahl paralleler Tiles |
| `--info` | `false` | Druckt Raster-Metadaten vor der Verarbeitung |
| `--verbose` | `false` | Druckt zusätzliche Detail-Logs für Read/Trace/Write |

## Verhalten und Annahmen

- Wenn weder `-b` noch `--bufferMeters` gesetzt ist, wird der Upstream-Default `tileSize / 3` als Pixelbuffer verwendet.
- `--outputByte` ist nur im Tiled-Modus erlaubt.
- Tiles ohne Daten im Kernbereich werden nicht berechnet.
- Wenn nur der Buffer Daten enthält, wird das Tile im Tiled-Modus nicht geschrieben; im Einzeldatei-Modus bleibt der Bereich NoData.
- `sunElevation` folgt der üblichen Semantik "Grad über dem Horizont".
- `ambientPower` wird additiv auf das gemittelte Ergebnis pro gültigem Pixel aufgeschlagen.
- `--algorithm horizon` unterstützt nur `maxBounces=0`; ein explizites `-r` wird in diesem Modus ignoriert.
- Wenn `--horizonRadiusMeters` gesetzt ist, wird der effektive Tile-Buffer automatisch auf mindestens diese Distanz vergrössert.
- Im `horizon`-Modus wird `--threads` direkt als Anzahl gleichzeitig berechneter Tiles verwendet; innerhalb eines Tiles bleibt die Berechnung einstufig.

## Performance-Hinweise

- CPU-only-Läufe mit `-r 1024` und `-t 2000` können sehr langsam sein.
- Für produktive CPU-Läufe sind meist kleinere Werte sinnvoll, zum Beispiel `-r 64..256` und `-t 256..1024`.
- Für grosse BBOXen sollte der Tiled-Modus bevorzugt werden.
- Für schnelle Vorschau- oder Näherungsläufe eignet sich `--algorithm horizon`, insbesondere mit `--horizonDirections 16..32`.

## Tests

Die Test-Suite deckt ab:

- BBOX-Snapping und Tile-Planung
- Deterministisches Ray-Sampling
- Lichtmodell und BVH-Hits
- NoData-/Skip-Logik
- Remote-Reads per HTTP-Range
- End-to-End-Läufe im Tiled- und Single-File-Modus

```bash
./gradlew test
```
