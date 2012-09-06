/*******************************************************************************
 * Copyright 2011, 2012 Torsten Hildebrandt and BIBA - Bremer Institut für Produktion und Logistik GmbH
 *
 * This file is part of jasima, v1.0.
 *
 * jasima is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * jasima is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with jasima.  If not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************/
package jasima.core.random.continuous;

import java.util.Random;

/**
 * Generates uniformly distributed doubles in the range [0,1.0). This class just
 * delegates to the underlying method {@link java.util.Random#nextDouble()}.
 * 
 * @author Torsten Hildebrandt
 */
public class DblUniformZeroOne extends DblStream {

	public DblUniformZeroOne() {
		this(null, null);
	}

	public DblUniformZeroOne(Random random) {
		this(random, null);
	}

	public DblUniformZeroOne(String name) {
		this(null, name);
	}

	public DblUniformZeroOne(Random random, String name) {
		super();
		setRndGen(random);
		setName(name);
	}

	@Override
	public double nextDbl() {
		return rndGen.nextDouble();
	}

	@Override
	public String toString() {
		return "DblUniformZeroOne";
	}

}
