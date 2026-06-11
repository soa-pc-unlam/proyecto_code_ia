package SheriffsssPackage;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;

public class GameInput implements KeyListener, MouseListener, MouseWheelListener, MouseMotionListener {
	private boolean leftPressed;
	private boolean rightPressed;
	private boolean upPressed;
	private boolean downPressed;
	private boolean interactHeld;
	private boolean mapHeld;
	private boolean escapeHeld;
	private boolean equipmentHeld;
	private boolean fullscreenHeld;
	private boolean primaryHeld;
	private boolean secondaryHeld;
	private boolean interactQueued;
	private boolean mapToggleQueued;
	private boolean escapeQueued;
	private boolean equipmentToggleQueued;
	private boolean fullscreenToggleQueued;
	private boolean primaryClickQueued;
	private boolean secondaryClickQueued;
	private int toolbarSelectionQueued = -1;
	private boolean trainingIncQueued;
	private boolean trainingDecQueued;
	private boolean trainingResetQueued;
	private boolean trainingSkipTutorialQueued;
	private boolean trainingBackToMenuQueued;
	private boolean trainingIncHeld;
	private boolean trainingDecHeld;
	private boolean trainingResetHeld;
	private boolean trainingSkipTutorialHeld;
	private boolean trainingBackToMenuHeld;
	private boolean zoomInHeld;
	private boolean zoomOutHeld;
	private int mouseX;
	private int mouseY;
	private int wheelRotationSteps;
	private int zoomWheelRotationSteps;
	private int zoomKeySteps;

	public int getMoveX() {
		return (this.leftPressed ? -1 : 0) + (this.rightPressed ? 1 : 0);
	}

	public int getMoveY() {
		return (this.upPressed ? -1 : 0) + (this.downPressed ? 1 : 0);
	}

	public void clearMovement() {
		this.leftPressed = false;
		this.rightPressed = false;
		this.upPressed = false;
		this.downPressed = false;
	}

	public void clearPrimaryAction() {
		this.primaryHeld = false;
		this.primaryClickQueued = false;
	}

	public boolean consumeInteractPressed() {
		boolean value = this.interactQueued;
		this.interactQueued = false;
		return value;
	}

	public boolean consumeMapToggle() {
		boolean value = this.mapToggleQueued;
		this.mapToggleQueued = false;
		return value;
	}

	public boolean consumeEscapePressed() {
		boolean value = this.escapeQueued;
		this.escapeQueued = false;
		return value;
	}

	public boolean consumeEquipmentToggle() {
		boolean value = this.equipmentToggleQueued;
		this.equipmentToggleQueued = false;
		return value;
	}

	public boolean consumeFullscreenToggle() {
		boolean value = this.fullscreenToggleQueued;
		this.fullscreenToggleQueued = false;
		return value;
	}

	public boolean consumePrimaryClick() {
		boolean value = this.primaryClickQueued;
		this.primaryClickQueued = false;
		return value;
	}

	public boolean consumeSecondaryClick() {
		boolean value = this.secondaryClickQueued;
		this.secondaryClickQueued = false;
		return value;
	}

	public int consumeToolbarSelection() {
		int value = this.toolbarSelectionQueued;
		this.toolbarSelectionQueued = -1;
		return value;
	}

	public boolean consumeTrainingIncrement() {
		boolean value = this.trainingIncQueued;
		this.trainingIncQueued = false;
		return value;
	}

	public boolean consumeTrainingDecrement() {
		boolean value = this.trainingDecQueued;
		this.trainingDecQueued = false;
		return value;
	}

	public boolean consumeTrainingReset() {
		boolean value = this.trainingResetQueued;
		this.trainingResetQueued = false;
		return value;
	}

	public boolean consumeTrainingSkipTutorial() {
		boolean value = this.trainingSkipTutorialQueued;
		this.trainingSkipTutorialQueued = false;
		return value;
	}

	public boolean consumeTrainingBackToMenu() {
		boolean value = this.trainingBackToMenuQueued;
		this.trainingBackToMenuQueued = false;
		return value;
	}

	public boolean isPrimaryHeld() {
		return this.primaryHeld;
	}

	public int consumeWheelSteps() {
		int steps = this.wheelRotationSteps;
		this.wheelRotationSteps = 0;
		return steps;
	}

	public int consumeZoomWheelSteps() {
		int steps = this.zoomWheelRotationSteps;
		this.zoomWheelRotationSteps = 0;
		return steps;
	}

	public int consumeZoomKeySteps() {
		int steps = this.zoomKeySteps;
		this.zoomKeySteps = 0;
		return steps;
	}

	public int getZoomKeyDirection() {
		return (this.zoomInHeld ? 1 : 0) + (this.zoomOutHeld ? -1 : 0);
	}

	public int getMouseX() {
		return this.mouseX;
	}

	public int getMouseY() {
		return this.mouseY;
	}

	@Override
	public void keyTyped(KeyEvent e) {
	}

	@Override
	public void keyPressed(KeyEvent e) {
		if (e.getKeyCode() == KeyEvent.VK_F11 || (e.getKeyCode() == KeyEvent.VK_ENTER && e.isAltDown())) {
			if (!this.fullscreenHeld) {
				this.fullscreenToggleQueued = true;
				this.fullscreenHeld = true;
			}
			return;
		}
		switch (e.getKeyCode()) {
			case KeyEvent.VK_A:
				this.leftPressed = true;
				break;
			case KeyEvent.VK_D:
				this.rightPressed = true;
				break;
			case KeyEvent.VK_W:
				this.upPressed = true;
				break;
			case KeyEvent.VK_S:
				this.downPressed = true;
				break;
			case KeyEvent.VK_E:
				if (!this.interactHeld) {
					this.interactQueued = true;
					this.interactHeld = true;
				}
				break;
			case KeyEvent.VK_M:
				if (!this.mapHeld) {
					this.mapToggleQueued = true;
					this.mapHeld = true;
				}
				break;
			case KeyEvent.VK_ESCAPE:
				if (!this.escapeHeld) {
					this.escapeQueued = true;
					this.escapeHeld = true;
				}
				break;
			case KeyEvent.VK_TAB:
				if (!this.equipmentHeld) {
					this.equipmentToggleQueued = true;
					this.equipmentHeld = true;
				}
				break;
			case KeyEvent.VK_1:
			case KeyEvent.VK_2:
			case KeyEvent.VK_3:
			case KeyEvent.VK_4:
			case KeyEvent.VK_5:
			case KeyEvent.VK_6:
			case KeyEvent.VK_7:
			case KeyEvent.VK_8:
			case KeyEvent.VK_9:
				this.toolbarSelectionQueued = e.getKeyCode() - KeyEvent.VK_1;
				break;
			case KeyEvent.VK_0:
				this.toolbarSelectionQueued = 9;
				break;
			case GameConfig.CAMERA_ZOOM_IN_KEY:
			case GameConfig.CAMERA_ZOOM_IN_KEY_ALT:
			case GameConfig.CAMERA_ZOOM_IN_KEY_NUMPAD:
				if (!this.zoomInHeld) {
					this.zoomKeySteps++;
					this.zoomInHeld = true;
				}
				break;
			case GameConfig.CAMERA_ZOOM_OUT_KEY:
			case GameConfig.CAMERA_ZOOM_OUT_KEY_NUMPAD:
				if (!this.zoomOutHeld) {
					this.zoomKeySteps--;
					this.zoomOutHeld = true;
				}
				break;
			case GameConfig.TRAINING_PANEL_INC_KEY:
				if (!this.trainingIncHeld) {
					this.trainingIncQueued = true;
					this.trainingIncHeld = true;
				}
				break;
			case GameConfig.TRAINING_PANEL_DEC_KEY:
				if (!this.trainingDecHeld) {
					this.trainingDecQueued = true;
					this.trainingDecHeld = true;
				}
				break;
			case GameConfig.TRAINING_PANEL_RESET_KEY:
				if (!this.trainingResetHeld) {
					this.trainingResetQueued = true;
					this.trainingResetHeld = true;
				}
				break;
			case GameConfig.TRAINING_TUTORIAL_SKIP_KEY:
				if (!this.trainingSkipTutorialHeld) {
					this.trainingSkipTutorialQueued = true;
					this.trainingSkipTutorialHeld = true;
				}
				break;
			case KeyEvent.VK_B:
				if (!this.trainingBackToMenuHeld) {
					this.trainingBackToMenuQueued = true;
					this.trainingBackToMenuHeld = true;
				}
				break;
			default:
				break;
		}
	}

	@Override
	public void keyReleased(KeyEvent e) {
		switch (e.getKeyCode()) {
			case KeyEvent.VK_A:
				this.leftPressed = false;
				break;
			case KeyEvent.VK_D:
				this.rightPressed = false;
				break;
			case KeyEvent.VK_W:
				this.upPressed = false;
				break;
			case KeyEvent.VK_S:
				this.downPressed = false;
				break;
			case KeyEvent.VK_E:
				this.interactHeld = false;
				break;
			case KeyEvent.VK_M:
				this.mapHeld = false;
				break;
			case KeyEvent.VK_ESCAPE:
				this.escapeHeld = false;
				break;
			case KeyEvent.VK_TAB:
				this.equipmentHeld = false;
				break;
			case KeyEvent.VK_F11:
			case KeyEvent.VK_ENTER:
				this.fullscreenHeld = false;
				break;
			case GameConfig.CAMERA_ZOOM_IN_KEY:
			case GameConfig.CAMERA_ZOOM_IN_KEY_ALT:
			case GameConfig.CAMERA_ZOOM_IN_KEY_NUMPAD:
				this.zoomInHeld = false;
				break;
			case GameConfig.CAMERA_ZOOM_OUT_KEY:
			case GameConfig.CAMERA_ZOOM_OUT_KEY_NUMPAD:
				this.zoomOutHeld = false;
				break;
			case GameConfig.TRAINING_PANEL_INC_KEY:
				this.trainingIncHeld = false;
				break;
			case GameConfig.TRAINING_PANEL_DEC_KEY:
				this.trainingDecHeld = false;
				break;
			case GameConfig.TRAINING_PANEL_RESET_KEY:
				this.trainingResetHeld = false;
				break;
			case GameConfig.TRAINING_TUTORIAL_SKIP_KEY:
				this.trainingSkipTutorialHeld = false;
				break;
			case KeyEvent.VK_B:
				this.trainingBackToMenuHeld = false;
				break;
			default:
				break;
		}
	}

	@Override
	public void mouseClicked(MouseEvent e) {
	}

	@Override
	public void mousePressed(MouseEvent e) {
		if (e.getButton() == MouseEvent.BUTTON1) {
			this.primaryClickQueued = true;
			this.primaryHeld = true;
		} else if (e.getButton() == MouseEvent.BUTTON3 && !this.secondaryHeld) {
			this.secondaryClickQueued = true;
			this.secondaryHeld = true;
		}
		updateMousePosition(e);
	}

	@Override
	public void mouseReleased(MouseEvent e) {
		if (e.getButton() == MouseEvent.BUTTON1) {
			this.primaryHeld = false;
		} else if (e.getButton() == MouseEvent.BUTTON3) {
			this.secondaryHeld = false;
		}
		updateMousePosition(e);
	}

	@Override
	public void mouseEntered(MouseEvent e) {
		updateMousePosition(e);
	}

	@Override
	public void mouseExited(MouseEvent e) {
		updateMousePosition(e);
	}

	@Override
	public void mouseWheelMoved(MouseWheelEvent e) {
		if (e.isControlDown()) {
			this.zoomWheelRotationSteps += e.getWheelRotation();
		} else {
			this.wheelRotationSteps += e.getWheelRotation();
		}
	}

	@Override
	public void mouseDragged(MouseEvent e) {
		updateMousePosition(e);
	}

	@Override
	public void mouseMoved(MouseEvent e) {
		updateMousePosition(e);
	}

	private void updateMousePosition(MouseEvent e) {
		this.mouseX = e.getX();
		this.mouseY = e.getY();
	}
}
