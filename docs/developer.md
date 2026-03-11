# Developer Notes

## Architektur

Das Projekt ist in diese Pakete gegliedert:

- `cli`: Picocli-basierte Kommandozeile
- `config`: Immutable Laufzeitkonfiguration und Beleuchtungsparameter
- `raster`: GeoTools/ImageIO-Ext-Zugriff auf lokale GeoTIFFs und Remote-COGs
- `tiling`: BBOX-Snapping, Buffer-Berechnung und Tile-Planung
- `core`: CPU-Raytracing, deterministic sampling, BVH und Tile-Verarbeitung
- `output`: GeoTIFF-Schreiber für tiled output und Einzeldatei-Akkumulator
- `pipeline`: Orchestrierung der parallelen Tile-Verarbeitung
- `util`: Konsolenlogger und Heartbeat-Scheduler

## Ablauf eines Runs

1. `OcclusionCommand` validiert Optionen und baut `RunConfig`.
2. `GeoToolsCogRasterSource` liest Metadaten des Eingangsraster und validiert:
   - Single-Band
   - Meter-Einheiten
   - Nordorientierung
   - keine Rotation/kein Shear
3. `TilePlanner` schneidet die BBOX auf den Rasterbereich zu, snappt auf Pixelgrenzen und erzeugt row-major `TileRequest`s.
4. `OcclusionPipeline` verarbeitet Tiles parallel über ein Fixed-Thread-Pool.
5. Für jedes Tile:
   - gepuffertes Fenster lesen
   - nur den Kernbereich auf Daten prüfen
   - bei Daten:
     - `exact`: BVH aus gepufferten Säulen erzeugen und Kernpixel per Raytracing berechnen
     - `horizon`: lokalen DEM-Pyramidenaufbau erzeugen und Kernpixel per Richtungs-Horizontprofil berechnen
   - Ergebnis schreiben

## Logging

- `ConsoleLogger` schreibt serialisierte Plain-Text-Logs mit Zeitstempel und Level-Präfix.
- `INFO` ist für normale Fortschrittsmeldungen gedacht.
- `VERBOSE` wird nur über `--verbose` aktiviert und deckt Detailmeldungen pro Tile sowie Reader-/Writer-Initialisierung ab.
- Die Pipeline startet zusätzlich einen Heartbeat im 30-Sekunden-Intervall, solange Arbeit in Flight ist.

## Raster-I/O

- Remote-Reads verwenden `GeoTiffReader` zusammen mit `CogSourceSPIProvider` und `HttpRangeReader`.
- Das aktuelle Lesen basiert auf einer lazy Coverage und `RenderedImage.getData(Rectangle)`, damit nur die tatsächlich benötigten Bereiche materialisiert werden.
- Jeder Worker nutzt eine eigene Reader-Session; GeoTools-Reader werden nicht zwischen Threads geteilt.

## Geometrie und Tracing

- Jede gültige DSM-Zelle wird als vertikale Säule modelliert:
  - Footprint: halbe Pixelgrösse in X/Y
  - Höhe: `elevation * exaggeration`
  - Unterkante: `z = 0`
- Die BVH wird pro gepuffertem Tile aufgebaut.
- Berechnet werden nur gültige Kernpixel, nicht der gesamte Buffer.
- Die Sampling-Strategie ist deterministisch und an Tile-ID, Pixelindex und Rayindex gebunden.
- Der Horizon-Mode verwendet statt BVH/Rays ein diskretes Richtungsprofil mit mehrstufiger Distanzabtastung und einer bias-kompatiblen Sichtbarkeitsfunktion.

## Einzeldatei-Modus

- `SingleFileAccumulator` hält ein temp-dateibasiertes Float-Raster (`DiskBackedFloatImage`).
- Jedes berechnete Tile schreibt nur seinen Kernbereich in dieses Raster.
- Am Ende wird daraus genau ein GeoTIFF geschrieben.

## Bekannte Grenzen

- Nur projizierte CRS mit Meter-Einheiten
- Keine rotierte/sheared Rastergeometrie
- Keine Multi-Band-Inputs
- Keine GPU-Implementierung
- `exact` splittet Threads zwischen parallelen Tiles und paralleler Zeilenberechnung innerhalb eines Tiles auf
- `horizon` parallelisiert nur über Tiles; die Berechnung innerhalb eines Tiles bleibt einstufig
- `--algorithm horizon` approximiert nur den No-Bounce-Fall (`maxBounces=0`)

## Tests

- Unit-Tests prüfen Tiling, Sampling, Beleuchtung, BVH und Skip-Logik.
- Integrationstests erzeugen ein Test-GeoTIFF, servieren es über einen lokalen HTTP-Range-Server und prüfen Remote-Subset-Reads.
- End-to-End-Tests decken tiled output, `--startTile` und Einzeldatei-Mosaikierung ab.

## Nützliche Befehle

```bash
./gradlew compileJava
./gradlew test
./gradlew run --args="--help"
```
