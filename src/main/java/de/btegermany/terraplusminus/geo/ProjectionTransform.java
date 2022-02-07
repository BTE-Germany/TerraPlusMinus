/*
 * Copyright (c) 2020 Noah Husby
 * Sledgehammer [Bungeecord] - ProjectionTransform.java
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

public abstract class ProjectionTransform extends GeographicProjection {
    protected GeographicProjection input;

    public ProjectionTransform(GeographicProjection input) {
        this.input = input;
    }

    public boolean upright() {
        return input.upright();
    }

    public double[] bounds() {
        return input.bounds();
    }

    public double metersPerUnit() {
        return input.metersPerUnit();
    }
}
