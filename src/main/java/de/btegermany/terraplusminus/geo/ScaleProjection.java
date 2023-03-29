/*
 * Copyright (c) 2020 Noah Husby
 * Sledgehammer [Bungeecord] - ScaleProjection.java
 *
 * Sledgehammer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Sledgehammer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Sledgehammer.  If not, see <https://github.com/noahhusby/Sledgehammer/blob/master/LICENSE/>.
 */

package de.btegermany.terraplusminus.geo;

public class ScaleProjection extends ProjectionTransform {

    double scaleX, scaleY;

    public ScaleProjection(GeographicProjection input, double scaleX, double scaleY) {
        super(input);
        this.scaleX = scaleX;
        this.scaleY = scaleY;
    }

    public ScaleProjection(GeographicProjection input, double scale) {
        this(input, scale, scale);
    }

    public double[] toGeo(double x, double y) {
        return input.toGeo(x / scaleX, y / scaleY);
    }

    public double[] fromGeo(double lon, double lat) {
        double[] p = input.fromGeo(lon, lat);
        p[0] *= scaleX;
        p[1] *= scaleY;
        return p;
    }

    public boolean upright() {
        return (scaleY < 0) ^ input.upright();
    }

    public double[] bounds() {
        double[] b = input.bounds();
        b[0] *= scaleX;
        b[1] *= scaleY;
        b[2] *= scaleX;
        b[3] *= scaleY;
        return b;
    }

    public double metersPerUnit() {
        return input.metersPerUnit() / Math.sqrt((scaleX * scaleX + scaleY * scaleY) / 2); //TODO: better transform
    }
}
