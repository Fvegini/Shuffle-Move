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

package shuffle.fwk.data.simulation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.RecursiveTask;
import java.util.logging.Level;
import java.util.logging.Logger;

import shuffle.fwk.GradingMode;
import shuffle.fwk.config.manager.EffectManager;
import shuffle.fwk.config.manager.RosterManager;
import shuffle.fwk.config.manager.SpeciesManager;
import shuffle.fwk.data.Board;
import shuffle.fwk.data.Effect;
import shuffle.fwk.data.Species;
import shuffle.fwk.data.Stage;
import shuffle.fwk.data.Team;
import shuffle.fwk.data.simulation.util.SimulationAcceptor;

/**
 * The core of the simulation for Shuffle Move.<br>
 * All simulation data is loaded from the user in the constructor, and allows any modification in
 * the user to be unseen in this object. This allows the simulation to be entirely multithreaded to
 * the limit of your hardware.
 * 
 * @author Andrew Meyers
 *         
 */
public class SimulationCore extends RecursiveAction {
   private static final long serialVersionUID = -4790004708567579267L;
   
   private static final Logger LOG = Logger.getLogger(SimulationCore.class.getName());
   
   static {
      LOG.setLevel(Level.FINE);
   }
   
   // Determines how far in the future the feeders will generate
   private final int minHeight;
   // Determines how many variations of possible boards will be used to simulate moves.
   // Increase this to improve result accuracy at the cost of processing time.
   private final int preferredCount;
   private final Board board;
   private final Set<Species> possibleBlocks;
   private final String megaSlotName;
   private final Map<String, Species> speciesMap;
   private final Map<Species, Integer> speciesLevels;
   private final Map<Species, Integer> speciesSkillLevels;
   private final Stage stage;
   private final Set<Species> supportSpecies;
   private final Set<Species> nonSupportSpecies;
   private final int megaProgress;
   private final int megaThreshold;
   private final boolean megaAllowed;
   private final int remainingHealth;
   private final int remainingMoves;
   private final SimulationAcceptor acceptor;
   private final UUID processUUID;
   private long startTime;
   private final Collection<Effect> disabledEffects;
   private final boolean attackPowerUp;
   private final int effectThreshold;
   private final EffectManager effectManager;
   private final GradingMode defaultGradingMode;
   private final boolean mobileMode;
   
   // Gets all the data it needs from the user, as deep copies of all relevant information.
   public SimulationCore(SimulationUser user, UUID processUUID) {
      this.processUUID = processUUID;
      minHeight = 0; // Math.max(0, user.getPreferredFeederHeight());
      preferredCount = Math.max(1, user.getPreferredNumFeeders());
      Board userBoard = user.getBoardManager().getBoard();
      board = new Board(userBoard);
      stage = user.getBoardManager().getCurrentStage();
      Team userTeam = user.getCurrentTeam();
      megaSlotName = userTeam.getMegaSlotName();
      megaProgress = user.getMegaProgress();
      megaAllowed = user.isMegaAllowed();
      RosterManager rosterManager = user.getRosterManager();
      SpeciesManager speciesManager = user.getSpeciesManager();
      effectManager = new EffectManager(user.getEffectManager());
      megaThreshold = userTeam.getMegaThreshold(speciesManager, rosterManager, effectManager);
      remainingHealth = user.getRemainingHealth();
      remainingMoves = user.getRemainingMoves() - 1;
      possibleBlocks = new HashSet<Species>();
      int numSpecies = userTeam.getNames().size();
      speciesMap = new HashMap<String, Species>(numSpecies);
      speciesLevels = new HashMap<Species, Integer>(numSpecies);
      speciesSkillLevels = new HashMap<Species, Integer>(numSpecies);
      supportSpecies = new HashSet<Species>(userTeam.getSpecies(speciesManager));
      Collection<Species> speciesPresent = new HashSet<Species>(supportSpecies);
      nonSupportSpecies = new HashSet<Species>(board.getSpeciesPresent());
      speciesPresent.addAll(nonSupportSpecies);
      nonSupportSpecies.removeAll(supportSpecies);
      for (Species s : speciesPresent) {
         speciesMap.put(s.getName(), s);
         speciesLevels.put(s, rosterManager.getLevelForSpecies(s));
         speciesSkillLevels.put(s, rosterManager.getSkillLevelForSpecies(s));
         if (s.getEffect().isAutoGenerated()) {
            possibleBlocks.add(s);
         }
         if (!supportSpecies.contains(s)) {
            nonSupportSpecies.add(s);
         }
      }
      acceptor = user;
      disabledEffects = user.getDisabledEffects();
      attackPowerUp = user.getAttackPowerUp();
      effectThreshold = user.getEffectThreshold();
      defaultGradingMode = user.getGradingModeManager().getDefaultGradingMode();
      mobileMode = user.isMobileMode();
   }
   
   public UUID getId() {
      return processUUID;
   }
   
   // Getters for use when creating primary SimulationStates
   protected Board getBoardCopy() {
      return new Board(board);
   }
   
   public Species getMegaSlot() {
      return speciesMap.get(megaSlotName);
   }
   
   public Species getSpecies(String name) {
      return speciesMap.get(name);
   }
   
   public int getLevel(Species s) {
      return speciesLevels.containsKey(s) ? speciesLevels.get(s) : 0;
   }
   
   public Stage getStage() {
      return stage;
   }
   
   public Set<Species> getSupportSpecies() {
      return Collections.unmodifiableSet(supportSpecies);
   }
   
   public Set<Species> getNonSupportSpecies() {
      return Collections.unmodifiableSet(nonSupportSpecies);
   }
   
   public int getMegaProgress() {
      return megaProgress;
   }
   
   public boolean isMegaAllowed() {
      return megaAllowed;
   }
   
   public int getMegaThreshold() {
      return megaThreshold;
   }
   
   public int getRemainingHealth() {
      return remainingHealth;
   }
   
   public int getRemainingMoves() {
      return remainingMoves;
   }
   
   public boolean isDisabledEffect(Effect e) {
      return disabledEffects.contains(e);
   }
   
   public int getEffectThreshold() {
      return effectThreshold;
   }
   
   public double getOdds(Effect effect, int num, int skillLevel) {
      return effectManager.getOdds(effect, num, skillLevel);
   }
   
   public double getMultiplier(Effect effect, int skillLevel) {
      return effectManager.getMult(effect, skillLevel);
   }
   
   public int getSkillLevel(Species species) {
      return speciesSkillLevels.get(species);
   }
   
   public boolean isMobileMode() {
      return mobileMode;
   }
   
   @Override
   protected void compute() {
      startTime = System.currentTimeMillis();
      try {
         Collection<SimulationResult> results = getResults();
         submitResults(results);
         releaseResources();
      } catch (Exception e) {
         LOG.log(Level.FINE, "Can't simulate because: " + e.getMessage(), e);
      }
   }
   
   /**
    * 
    */
   private void releaseResources() {
      possibleBlocks.clear();
      speciesMap.clear();
      speciesLevels.clear();
      System.gc();
   }
   
   /**
    * 
    */
   private Collection<SimulationResult> getResults() {
      Collection<SimulationResult> results = computeWithoutMove();
      if (results != null) {
         return results;
      }
      long start = System.currentTimeMillis();
      LOG.fine("Preparing board, moves & feeder");
      // First, generate the valid moves and the feeders required.
      List<List<Integer>> validMoves = getPossibleMoves(board);
      Collection<SimulationFeeder> feeders = SimulationFeeder.getFeedersFor(minHeight, getStage(), possibleBlocks,
            preferredCount);
            
      Map<List<Integer>, SimulationCreationTask> moveToTaskCreatorMap = new HashMap<List<Integer>, SimulationCreationTask>();
      
      long diff = System.currentTimeMillis() - start;
      LOG.fine("Making tasks, prep took " + diff + "ms");
      start = System.currentTimeMillis();
      
      // Go through all moves and create a task for each feeder, and add it.
      for (List<Integer> move : validMoves) {
         SimulationCreationTask distTask = new SimulationCreationTask(this, move, feeders);
         distTask.fork();
         moveToTaskCreatorMap.put(move, distTask);
      }
      
      Map<List<Integer>, Collection<SimulationTask>> moveToTasksMap = new HashMap<List<Integer>, Collection<SimulationTask>>();
      for (List<Integer> move : validMoves) {
         Collection<SimulationTask> taskSet = moveToTaskCreatorMap.get(move).join();
         moveToTasksMap.put(move, taskSet);
      }
      
      diff = System.currentTimeMillis() - start;
      LOG.fine("Getting results, init took " + diff + "ms");
      
      start = System.currentTimeMillis();
      // Once done, we go through the results and find the best on-average result
      results = getBestResults(validMoves, moveToTasksMap);
      diff = System.currentTimeMillis() - start;
      LOG.fine("Returning best results, the decision took " + diff + "ms");
      return results;
   }
   
   /**
    * @return
    */
   public Collection<SimulationResult> computeWithoutMove() {
      Collection<SimulationFeeder> feeders = SimulationFeeder.getFeedersFor(0, getStage(), possibleBlocks,
            preferredCount);
      Collection<SimulationTask> toRun = new SimulationCreationTask(this, null, feeders).invoke();
      ForkJoinTask<SimulationResult> assembler = new SimulationResultsAssembler(null, processUUID, toRun, startTime)
            .fork();
      SimulationResult settleResult = assembler.join();
      if (settleResult.getBoard().equals(board)) {
         return null;
      } else {
         return Arrays.asList(settleResult);
      }
   }
   
   /**
    * @param results
    */
   private void submitResults(Collection<SimulationResult> results) {
      if (isCurrent()) {
         // Finally, we distribute it to the acceptor, and return it as well for open compatibility.
         acceptor.acceptResults(results);
         
         System.gc();
      } else {
         LOG.fine("Results discarded - out of date info");
      }
   }
   
   /**
    * @param validMoves
    * @param moveToTasksMap
    * @return
    */
   private Collection<SimulationResult> getBestResults(List<List<Integer>> validMoves,
         Map<List<Integer>, Collection<SimulationTask>> moveToTasksMap) {
      // Now we need to combine all the results and obtain the best move and likely SimulationResult
      
      Map<List<Integer>, RecursiveTask<SimulationResult>> compiledResultsMap = new HashMap<List<Integer>, RecursiveTask<SimulationResult>>();
      
      for (List<Integer> move : validMoves) {
         Collection<SimulationTask> results = moveToTasksMap.get(move);
         SimulationResultsAssembler assembler = new SimulationResultsAssembler(move, processUUID, results, startTime);
         assembler.fork();
         compiledResultsMap.put(move, assembler);
      }
      
      TreeSet<SimulationResult> bestResultSet = new TreeSet<SimulationResult>(defaultGradingMode.getGradingMetric());
      for (List<Integer> move : validMoves) {
         SimulationResult result = compiledResultsMap.get(move).join();
         if (result != null) {
            bestResultSet.add(result);
         }
      }
      return bestResultSet;
   }
   
   public static List<List<Integer>> getPossibleMoves(Board b) {
      // First, get all pick and drop locations.
      List<List<Integer>> pickables = new ArrayList<List<Integer>>();
      List<List<Integer>> dropables = new ArrayList<List<Integer>>();
      for (int row = 1; row <= Board.NUM_COLS; row++) {
         for (int col = 1; col <= Board.NUM_ROWS; col++) {
            Effect effect = b.getSpeciesAt(row, col).getEffect();
            if (effect.isDroppable() && !b.isFrozenAt(row, col)) {
               List<Integer> coord = Arrays.asList(row, col);
               dropables.add(coord);
               if (effect.isPickable()) {
                  pickables.add(coord);
               }
            }
         }
      }
      // Then only include those combinations which could actually be a valid
      // move (allowed by the game engine)
      List<List<Integer>> ret = new ArrayList<List<Integer>>();
      for (List<Integer> pick : pickables) {
         for (List<Integer> drop : dropables) {
            if (isAllowed(pick, drop, b)) {
               ret.add(Arrays.asList(pick.get(0), pick.get(1), drop.get(0), drop.get(1)));
            }
         }
      }
      return ret;
   }
   
   /**
    * Checks if the given pickup and dropon move is allowed for the given board.
    * 
    * @param pickup
    *           The coordinates that are picked up
    * @param dropon
    *           The coordinates that are dropped on
    * @param b
    *           The board state
    * @return True if allowed, false if otherwise.
    */
   public static boolean isAllowed(List<Integer> pickup, List<Integer> dropon, Board b) {
      // First, check that the pickup and dropat are not frozen, that the pick
      // is pickable, that the drop is droppable, and that the dropat
      // coordinates immediately result in a combo of some kind.
      
      Species pickedUpSpecies = b.getSpeciesAt(pickup.get(0), pickup.get(1));
      Species droppedOnSpecies = b.getSpeciesAt(dropon.get(0), dropon.get(1));
      
      boolean allowed = !pickup.equals(dropon) && !pickedUpSpecies.equals(droppedOnSpecies)
            && !b.isFrozenAt(pickup.get(0), pickup.get(1)) && !b.isFrozenAt(dropon.get(0), dropon.get(1));
      // If allowed is still possible, then we proceed to check for a combo at the destination
      if (allowed) {
         Board afterSwap = new Board(b);
         afterSwap.setSpeciesAt(dropon.get(0), dropon.get(1), pickedUpSpecies);
         afterSwap.setSpeciesAt(pickup.get(0), pickup.get(1), droppedOnSpecies);
         allowed &= madeACombo(dropon, pickedUpSpecies, afterSwap) || madeACombo(pickup, droppedOnSpecies, afterSwap);
      }
      return allowed;
   }
   
   /**
    * Checks if the given coordinates and species creates some kind of combo in the given board.
    * 
    * @param coords
    *           Where the check is performed around
    * @param species
    *           The species that is being checked for combos.
    * @param board
    *           The board state.
    * @return True if there is a combo, false if otherwise.
    */
   private static boolean madeACombo(List<Integer> coords, Species species, Board board) {
      if (!species.getEffect().isPickable()) {
         return false;
      }
      final int row = coords.get(0);
      final int col = coords.get(1);
      final int[] vLines = new int[5];
      final int[] hLines = new int[5];
      for (int i = 0; i < 5; i++) {
         // Propagate vertical species matches
         int vPrev = i == 0 ? 0 : vLines[i - 1];
         if (i == 2 || board.getSpeciesAt(row - 2 + i, col).equals(species)) {
            vLines[i] = vPrev + 1;
         } else {
            vLines[i] = 0;
         }
         // Propagate horizontal species matches
         int hPrev = i == 0 ? 0 : hLines[i - 1];
         if (i == 2 || board.getSpeciesAt(row, col - 2 + i).equals(species)) {
            hLines[i] = hPrev + 1;
         } else {
            hLines[i] = 0;
         }
         // Found a match
         if (hLines[i] >= 3 || vLines[i] >= 3) {
            return true;
         }
      }
      return false;
   }
   
   public boolean isCurrent() {
      return acceptor.getAcceptedId().equals(processUUID);
   }
   
   /**
    * @return
    */
   public boolean isAttackPowerUp() {
      return attackPowerUp;
   }
}
