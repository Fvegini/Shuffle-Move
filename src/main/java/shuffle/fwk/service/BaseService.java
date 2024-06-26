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

package shuffle.fwk.service;

import java.awt.Frame;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import java.awt.event.KeyEvent;

import shuffle.fwk.config.ConfigManager;

/**
 * The base service implementation, which takes care of creation, maintentance, and disposal.
 * 
 * @author Andrew Meyers
 */
public abstract class BaseService<Y extends Object> implements Service<Y> {
   
   protected static final String KEY_POPUP_WIDTH = "POPUP_WIDTH";
   protected static final String KEY_POPUP_HEIGHT = "POPUP_HEIGHT";
   public static final String KEY_WINDOW_SAFETY_ENABLED = "WINDOW_SAFETY_ENABLED";
   protected static final int DEFAULT_POPUP_WIDTH = 640;
   protected static final int DEFAULT_POPUP_HEIGHT = 400;
   /** The id which is unique across all instances and identifies this service. */
   protected final Long id;
   /** The optionally set dialog which is maintained by the service. */
   private JDialog dialog = null;
   /** The owner of this service */
   private Frame owner = null;
   /** If disposal is allowed. */
   private boolean disposeEnabled = true;
   /** The button to be activated when Enter is hit. */
   private JButton defaultButton = null;
   
   /**
    * The constructor for a service.
    */
   protected BaseService() {
      id = BaseServiceManager.getNewId(this);
   }
   
   /**
    * Returns the unique id for this service.
    */
   @Override
   public long getId() {
      return id;
   }
   
   /**
    * Gets the current user for this service.
    * 
    * @return The current user. Null if there is none.
    */
   protected final Y getUser() {
      return BaseServiceManager.getUser(this, getUserClass());
   }
   
   /**
    * Gets the class of a service user.
    * 
    * @return the class of this service's users.
    */
   protected abstract Class<Y> getUserClass();
   
   /**
    * Sets up the GUI for this service. After this call, the interface should be ready for launch.
    */
   @Override
   public final void setupGUI(Frame owner) {
      setOwner(owner);
      onSetupGUI();
   }
   
   protected abstract void onSetupGUI();
   
   private void setOwner(Frame owner) {
      this.owner = owner;
   }
   
   protected final Frame getOwner() {
      return owner;
   }
   
   /**
    * Updates the GUI from the current user. After this call, the interface should reflect the data
    * currently available from its user.
    */
   @Override
   public void updateGUI() {
      updateGUIFrom(getUser());
   }
   
   /**
    * Updates the GUI from the given user. After this call, the interface should reflect the data
    * currently available from its user.
    * 
    * @param user
    *           The user to update from. if null, this call will do nothing. (silent ignore)
    */
   protected abstract void updateGUIFrom(Y user);
   
   /**
    * Sets the dialog for this service to the specified dialog.
    * 
    * @param dialog
    *           The dialog for this service to maintain.
    */
   protected void setDialog(JDialog dialog) {
      this.dialog = dialog;
      dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
      dialog.addWindowListener(new WindowAdapter() {
         @Override
         public void windowClosing(WindowEvent e) {
            dispose();
         }
      });
      JRootPane root = dialog.getRootPane();
      root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "closeDialog");
      root.getActionMap().put("closeDialog", new AbstractAction() {
         private static final long serialVersionUID = 2759447330549410101L;
         
         @Override
         public void actionPerformed(ActionEvent e) {
            BaseService.this.dispose();
         }
      });
      root.setDefaultButton(defaultButton);
   }
   
   protected void setDefaultButton(JButton button) {
      defaultButton = button;
      if (dialog != null) {
         dialog.getRootPane().setDefaultButton(button);
      }
   }

   @Override
   public JDialog getDialog() {
      return dialog;
   }
   
   protected void setDisposeEnabled(boolean enabled) {
      disposeEnabled = enabled;
   }
   
   private boolean isDisposeEnabled() {
      return disposeEnabled;
   }
   
   /**
    * Launches the GUI for this service. This will first call {@link #updateGUI()} to ensure that
    * the data is valid.
    */
   @Override
   public final void launch() {
      doSafely(new Runnable() {
         @Override
         public void run() {
            updateGUI();
            if (dialog != null && !dialog.isVisible()) {
               dialog.setVisible(true);
            }
            onLaunch();
         }
      });
   }
   
   /**
    * Called immediately after a call to {@link #launch()}, any subclass which requires special
    * behavior should override this method.
    */
   protected void onLaunch() {
      // Empty base implementation.
   }
   
   @Override
   public final void pullToFront() {
      doSafely(new Runnable() {
         @Override
         public void run() {
            if (dialog != null && dialog.isVisible()) {
               dialog.toFront();
               dialog.repaint();
            }
         }
      });
   }
   
   /**
    * Hides the GUI for this service. This will not send data or do anything other than hiding the
    * dialog (unless the subclass implements {@link #onHide()}).
    */
   @Override
   public final void hide() {
      doSafely(new Runnable() {
         @Override
         public void run() {
            if (dialog != null && dialog.isVisible()) {
               dialog.setVisible(false);
            }
            onHide();
         }
      });
   }
   
   /**
    * Called immediately after a call to {@link #hide()}, any subclass which requires special
    * behavior should override this method.
    */
   protected void onHide() {
      // Empty base implementation.
   }
   
   /**
    * Disposes the GUI for this service and deregisters it from the {@link BaseServiceManager}. This
    * will not send data or do anything other than disposing the dialog (unless the subclass
    * implements {@link #onDispose()}).
    */
   protected final void dispose() {
      if (isDisposeEnabled()) {
         doSafely(new Runnable() {
            @Override
            public void run() {
               onDispose();
               if (dialog != null) {
                  dialog.setVisible(false);
                  dialog.dispose();
                  dialog = null;
               }
               BaseServiceManager.unregisterService(id);
            }
         });
      }
   }
   
   /**
    * Called immediately before a call to {@link #dispose()}, any subclass which requires special
    * behavior should override this method.
    */
   protected void onDispose() {
      // Empty base implementation.
   }
   
   @Override
   public final int hashCode() {
      return id.hashCode();
   }
   
   @Override
   public final boolean equals(Object o) {
      return o != null && o instanceof BaseService<?> && ((BaseService<?>) o).id.equals(id);
   }
   
   public static void doSafely(Runnable run) {
      if (SwingUtilities.isEventDispatchThread()) {
         run.run();
      } else {
         SwingUtilities.invokeLater(run);
      }
   }
   
   /**
    * Given a window object, and a proposed x and y position on the screen, perform any necessary
    * correction to force it to remain on at least one of the available display devices. If the top
    * right corner of the window is not within any display device, it will be moved to the default
    * location.
    * 
    * @param w
    *           A Window that is moving, or about to be moved.
    * @param x
    *           The relative horizontal position to the display origin
    * @param y
    *           The relative vertical position to the display origin
    */
   public static void correctPosition(Window w, int x, int y, ConfigManager pm) {
      boolean isMaximizedEither = false;
      if (w != null && Frame.class.isInstance(w)) {
         int state = ((Frame) w).getExtendedState();
         boolean maximizedHoriz = (state & JFrame.MAXIMIZED_HORIZ) != 0;
         boolean maximizedVert = (state & JFrame.MAXIMIZED_VERT) != 0;
         isMaximizedEither = maximizedHoriz || maximizedVert;
      }
      
      if (pm.getBooleanValue(KEY_WINDOW_SAFETY_ENABLED, true) && !isLocationInScreenBounds(x, y)
            && !isMaximizedEither) {
         w.setLocationRelativeTo(null);
      }
   }
   
   public static final boolean isLocationInScreenBounds(int x, int y) {
      // From:
      // http://www.java2s.com/Code/Java/Swing-JFC/Verifiesifthegivenpointisvisibleonthescreen.htm
      
      // Check if the location is in the bounds of one of the graphics devices.
      GraphicsEnvironment graphicsEnvironment = GraphicsEnvironment.getLocalGraphicsEnvironment();
      GraphicsDevice[] graphicsDevices = graphicsEnvironment.getScreenDevices();
      Rectangle graphicsConfigurationBounds = new Rectangle();
      
      // Iterate over the graphics devices.
      for (int j = 0; j < graphicsDevices.length; j++) {
         
         // Get the bounds of the device.
         GraphicsDevice graphicsDevice = graphicsDevices[j];
         graphicsConfigurationBounds.setRect(graphicsDevice.getDefaultConfiguration().getBounds());
         
         // Is the location in this bounds?
         graphicsConfigurationBounds.setRect(graphicsConfigurationBounds.x, graphicsConfigurationBounds.y,
               graphicsConfigurationBounds.width, graphicsConfigurationBounds.height);
         if (graphicsConfigurationBounds.contains(x, y)) {
            
            // The location is in this screengraphics.
            return true;
            
         }
         
      }
      
      // We could not find a device that contains the given point.
      return false;
   }
}
