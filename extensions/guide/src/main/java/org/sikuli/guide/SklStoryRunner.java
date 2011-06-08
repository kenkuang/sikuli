package org.sikuli.guide;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToolBar;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.Timer;
import javax.swing.border.EtchedBorder;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

import org.sikuli.script.Debug;

import com.sun.medialib.mlib.Image;


interface StoryRunnerListener{
   
   void storyStarted();
   void storyCompleted();
   void storyStopped();
   void storyFailed(int index);
   
}

public class SklStoryRunner extends JPanel implements KeyListener {
   
   
   ArrayList<StoryRunnerListener> _listeners = new ArrayList<StoryRunnerListener>();
   
   void addListener(StoryRunnerListener listener){
      if (!_listeners.contains(listener))
         _listeners.add(listener);
   }
   
   void fireStoryCompleted(){
      for (StoryRunnerListener listener : _listeners){
         listener.storyCompleted();
      }
   }
   
   void fireStoryStarted(){
      for (StoryRunnerListener listener : _listeners){
         listener.storyStarted();
      }
   }

   
   void fireStoryStopped(){
      for (StoryRunnerListener listener : _listeners){
         listener.storyStopped();
      }
   }

   void fireStoryFailed(int index){
      for (StoryRunnerListener listener : _listeners){
         listener.storyFailed(index);
      }
   }

   
   enum StepStatus{
      PASSED,
      FAILED,
      SKIPPED,
      RUNNING,
      STOPPED
   };
   
   enum StoryStatus{
      RUNNING,
      STOPPED,
      FAILED,
      COMPLETED
   };
   
   private class ListCellTile extends JPanel{
      
      JLabel _indexLabel = new JLabel();
      JLabel _statusLabel = new JLabel("     ");
      JPanel inner;
      
      ListCellTile(JComponent comp){        
         setOpaque(true);
         setLayout(new BorderLayout());
         setBorder(BorderFactory.createEmptyBorder(5,0,5,0));         
         
         
         inner = new JPanel();
         inner.setLayout(new BorderLayout());
         
         
         _indexLabel.setFont(new Font("sansserif", Font.BOLD, 16));
         inner.add(_indexLabel, BorderLayout.WEST);
         inner.add(_statusLabel, BorderLayout.SOUTH);         
         inner.add(comp, BorderLayout.CENTER);
         inner.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));         

         
         add(inner, BorderLayout.CENTER);
         
//         setPreferredSize(new Dimension(200,200));
//         setMinimumSize(new Dimension(200,200));
      }
      
      void setIndex(int index){
         _indexLabel.setText(""+(index+1));
      }
      
      void setSelected(boolean selected){
//         if (selected){
//            inner.setBackground(new Color(250,250,210));
//         }
      }
      
      void setStatus(StepStatus status){
         
         _statusLabel.setForeground(Color.black);

         
         if (status == StepStatus.PASSED){
            inner.setBackground(Color.green);
            _statusLabel.setText("Passed");
         } else if (status == StepStatus.FAILED){
            inner.setBackground(Color.red);
            _statusLabel.setText("Failed");
         } else if (status == StepStatus.SKIPPED){
            inner.setBackground(new Color(135,206,250));
            _statusLabel.setText("Skipped");            
         } else if (status == StepStatus.RUNNING){
            inner.setBackground((new Color(30,144,255)).darker());
            _statusLabel.setForeground(Color.white);
            _statusLabel.setText("Running ...");
         } else if (status == StepStatus.STOPPED){
            inner.setBackground(Color.red);
            _statusLabel.setText("Stopped");
         } else {
            inner.setBackground(null);
            _statusLabel.setText("      ");
         }
         
      }
      
   }

   private Map<SklStepModel, JComponent> _stepModelViewMap = new HashMap<SklStepModel, JComponent>();
   private Map<SklStepModel, StepStatus> _stepModelStatusMap = new HashMap<SklStepModel, StepStatus>();
   
   private class Renderer implements ListCellRenderer{
      
      @Override
      public Component getListCellRendererComponent(JList list,
            Object value,
            int index,
            boolean isSelected,
            boolean cellHasFocus) {
         if (_document != null){
            
            SklStepModel stepModel = _document.getStep(index);
            stepModel.setSelected(isSelected);         
            
            JComponent view = _stepModelViewMap.get(stepModel);
            StepStatus status = _stepModelStatusMap.get(stepModel);
            
            if (view == null){
               JComponent v = new SklStepForegroundView(stepModel);
               view = new ListCellTile(v);
               
               _stepModelViewMap.put(stepModel, view);
            }
            
            ((ListCellTile) view).setIndex(index);
            ((ListCellTile) view).setSelected(isSelected);
            ((ListCellTile) view).setStatus(status);
            
            int d = SklStoryRunner.this.getSize().width;
            d = (int) (d*0.75);
             _list.setFixedCellHeight(d);
             _list.setFixedCellWidth(d);
             
            return view;
         }
         else{
            return null;
         }
      }
      
   }
   
   private SklDocument _document;
   
   private ToolBar _controlPanel;
   private JList _list;
   private SklStepPlayView _stepPlayer;
   
   SklStoryRunner() { 
      
      setLayout(new BorderLayout());

      _list = new JList();
      _list.setCellRenderer(new Renderer());
      _list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      _list.setVisibleRowCount(-1);
      _list.addKeyListener(this);      
      _list.setEnabled(false);
      
      
      _stepPlayer = new SklStepPlayView();
      _stepPlayer.addListener(new StepPlayerListener(){

         @Override
         public void stepCompleted(SklStepModel step) {
            _currentStepCtrl.passStep();
         }

         @Override
         public void stepFailed(SklStepModel step) {
            _currentStepCtrl.failStep();
         }
         
      });
//      
      JScrollPane scrollPane = new JScrollPane(_list);
      scrollPane.setPreferredSize(new Dimension(200, 200));
      add(scrollPane,BorderLayout.CENTER);
      
      _controlPanel = new ToolBar("Runner");
      add(_controlPanel, BorderLayout.NORTH);
      
      
      setFocusable(true);      
   }
   
   private class ToolBar extends JToolBar{
      
      AbstractAction runAction;
      AbstractAction stopAction;
      AbstractAction skipAction;
      ToolBar(String name){
         super(name);
         
         runAction = new AbstractAction("Run"){

            @Override
            public void actionPerformed(ActionEvent arg0) {
               runStory(0);
            }
            
            
         }; 
         
         add(runAction);
         
         stopAction = new AbstractAction("Stop"){     
         
            @Override
            public void actionPerformed(ActionEvent arg0) {
               stopStory();
            }
            
            
         }; 
         add(stopAction);
         
         skipAction = new AbstractAction("Skip"){

            @Override
            public void actionPerformed(ActionEvent arg0) {
               _currentStepCtrl.skipStep();
            }
            
         };
         
         add(skipAction);

      }
   }
   
   void run(SklDocument document){
      run(document, 0);
   }
   
   void run(SklDocument document, int startIndex){
      setStory(document);
      runStory(startIndex);
   }
   
   void runStory(int startIndex){
      fireStoryStarted();

      setStatus(StoryStatus.RUNNING);
           
      _stepModelStatusMap.clear();      
      _currentStepCtrl.setStep(startIndex);
      _currentStepCtrl.runStep();
   }
   
   private class CurrentStepController {
      
      SklStepModel _currentStep;
      int _index;
      
      void setStep(int index){
         _list.setSelectedIndex(index);
         _list.ensureIndexIsVisible(index);
         
       
         _index = index;
         _currentStep = _document.getStep(index);
      }
 
      boolean hasNextStep(){
         return (_index  < _document.getSteps().size()-1);
      }
      
      void nextStep(){
         if (hasNextStep()){
           setStep(_index + 1);
           runStep();
         }else{
           completeStory();
         }
      }
      
      void stopStep(){
         _stepModelStatusMap.put(_currentStep, StepStatus.STOPPED);      
         _stepPlayer.stop();
      }
      
      void skipStep(){
         _stepModelStatusMap.put(_currentStep, StepStatus.SKIPPED);      
         repaint();
         nextStep();
      }
      
      void passStep(){
         _stepModelStatusMap.put(_currentStep, StepStatus.PASSED);      
         repaint();
         nextStep();
      }
      
      void failStep(){
         _stepModelStatusMap.put(_currentStep, StepStatus.FAILED);      
         repaint();
         failStory();
      }
      
      void runStep(){
         
         stopStep();
         
         _stepModelStatusMap.put(_currentStep, StepStatus.RUNNING);      

         _stepPlayer.setVisible(true);
         _stepPlayer.play(_currentStep);   
      }
   }
   
   private CurrentStepController _currentStepCtrl = new CurrentStepController();
   
   private void setStatus(StoryStatus status){
      if (status == StoryStatus.RUNNING){
         _controlPanel.runAction.setEnabled(false);
         _controlPanel.stopAction.setEnabled(true);
         _controlPanel.skipAction.setEnabled(true);
      }else if (status == StoryStatus.FAILED){
         _controlPanel.runAction.setEnabled(true);
         _controlPanel.stopAction.setEnabled(false);
         _controlPanel.skipAction.setEnabled(false);
      }else if (status == StoryStatus.STOPPED){
         _controlPanel.runAction.setEnabled(true);
         _controlPanel.stopAction.setEnabled(false);
         _controlPanel.skipAction.setEnabled(false);
      }else if (status == StoryStatus.COMPLETED){
         _controlPanel.runAction.setEnabled(true);
         _controlPanel.stopAction.setEnabled(false);
         _controlPanel.skipAction.setEnabled(false);
      }
   }
   
   private void failStory(){

      setStatus(StoryStatus.FAILED);
      
      _currentStepCtrl.stopStep();
      
      _stepPlayer.setVisible(false);
      //fireStoryFailed(_currentIndex);
      fireStoryFailed(0);
   }
   
   private void stopStory(){
      
      setStatus(StoryStatus.STOPPED);

      _currentStepCtrl.stopStep();
      _stepPlayer.setVisible(false);
      fireStoryStopped();
   }
   
   private void completeStory(){
      Debug.info("[Story runner] complete");
      
      setStatus(StoryStatus.COMPLETED);
      
      _stepPlayer.setVisible(false);
      fireStoryCompleted();
   }
   
   void setStory(SklDocument document){
      _document = document;           
      _list.setModel(_document.getListModel());
   }
   
   @Override
   public void keyPressed(KeyEvent k) {
      //Debug.log("pressed " + k.getKeyCode());
      
      if (k.getKeyCode() == KeyEvent.VK_BACK_SPACE){
         Debug.log("User pressed DELETE");
         
       //  int index = _list.getSelectedIndex();
        // _document.removeStep(index);
      }
   }

   @Override
   public void keyReleased(KeyEvent arg0) {
   }

   @Override
   public void keyTyped(KeyEvent arg0) {
   }
}
