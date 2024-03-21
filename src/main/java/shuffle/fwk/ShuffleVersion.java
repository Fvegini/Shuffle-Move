/*  ShuffleMove - A program for identifying and simulating ideal moves in the game
 *  called Pokemon Shuffle.
 *  
 *  Copyright (C) 2015  Andrew Meyers
 *  
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *  
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package shuffle.fwk;

/**
 * The current build version, used for config synchronizations.
 * 
 * @author Andrew Meyers
 */
public interface ShuffleVersion {
   public static final long BUILD_DATE = 1706287861689l;
   /** The Major version number. 0 = unfinished, 1 = first release, etc. */
   public static final int VERSION_MAJOR = 1;
   /** The Minor version number. Each increment is a new significant overhaul. */
   public static final int VERSION_MINOR = 0;
   /** The SubMinor version number. Each increment is a minor batch of tweaks and fixes. */
   public static final int VERSION_SUBMINOR = 2;
   /** The full version String which identifies the program's actual version. */
   public static final String VERSION_FULL = String.format("v%d.%d.%d", VERSION_MAJOR, VERSION_MINOR, VERSION_SUBMINOR);
   /** Convenience array of version numbers for comparisons. */
   public static final int[] VERSION_ARRAY = new int[]{VERSION_MAJOR, VERSION_MINOR, VERSION_SUBMINOR};
   
}
