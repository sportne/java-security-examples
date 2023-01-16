package my.git.mailmap;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.WindowConstants;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import my.git.mailmap.gui.AuthorTreeNode;
import my.git.mailmap.gui.MailMapAuthorTreeNode;
import my.git.mailmap.gui.MailMapAuthorTreeRoot;
import my.git.mailmap.gui.SortedListModel;
import my.git.mailmap.gui.TreeTransferHandler;

public class MailMapApp extends JFrame {

   private static final long serialVersionUID = -6112098575191717560L;

   public static void main(String[] args) {
      new MailMapApp();
   }

   private final Set<Author> unassignedAuthorsSet;
   private final JList<Author> unassignedAuthorsList;
   private final Set<Author> primaryAuthorSet;
   private final JTree mailmapTree;

   private JMenuBar menuBar;
   private JMenu fileMenu;
   private JMenuItem openMenuItem;
   private JMenuItem loadGitRepoItem;
   private JMenuItem saveMenuItem;

   private JButton addPrimaryAuthorButton;
   private JButton addAlternativeAuthorButton;
   private JButton removeEntryButton;

   public MailMapApp() {

      // Create JMenuBar
      menuBar = new JMenuBar();
      fileMenu = new JMenu("File");
      openMenuItem = new JMenuItem("Load mailmap");
      loadGitRepoItem = new JMenuItem("Load git repo");
      saveMenuItem = new JMenuItem("Save mailmap");

      // Create JLists
      Comparator<Author> authorComparator = (o1, o2) -> o1.toString()
            .compareToIgnoreCase(o2.toString());
      unassignedAuthorsSet = new HashSet<>();
      unassignedAuthorsList = new JList<>(new SortedListModel<>(authorComparator));

      primaryAuthorSet = new HashSet<>();

      MailMapAuthorTreeRoot root = new MailMapAuthorTreeRoot();
      DefaultTreeModel treeModel = new DefaultTreeModel(root);
      mailmapTree = new JTree(treeModel);

      // Create JButtons
      addPrimaryAuthorButton = new JButton("NEW >>");
      addAlternativeAuthorButton = new JButton("ALT >>");
      removeEntryButton = new JButton("<<");

      setupLayout();

      setupControl();

      pack();

      setVisible(true);
   }

   private List<MailMapEntry> collectAllEntries() {
      MailMapAuthorTreeRoot root = getRoot();
      List<MailMapEntry> allEntries = new ArrayList<>();
      for (TreeNode node : root.getChildren()) {

         if (node instanceof MailMapAuthorTreeNode) {
            allEntries.addAll(((MailMapAuthorTreeNode) node).getMailMapAuthor().toEntries());
         }
      }

      return allEntries;
   }

   private MailMapAuthorTreeNode find(Author author) {
      MailMapAuthorTreeRoot root = getRoot();

      for (TreeNode node : root.getChildren()) {
         if ((node instanceof MailMapAuthorTreeNode) && ((MailMapAuthorTreeNode) node)
               .getMailMapAuthor().getPrimaryAuthor().equals(author)) {
            return (MailMapAuthorTreeNode) node;
         }
      }

      return null;
   }

   private ActionListener getAlternativeButtonActionListener() {
      return e -> {
         // Retrieve selected author from unassignedAuthorsList
         Author selectedAuthor = unassignedAuthorsList.getSelectedValue();
         if (selectedAuthor != null) {
            MailMapAuthor selectedPrimaryAuthor = getSelectedPrimaryAuthor();
            if (selectedPrimaryAuthor != null) {
               makeSecondaryAddition(selectedAuthor, selectedPrimaryAuthor);
            } else {
               JOptionPane.showMessageDialog(addAlternativeAuthorButton,
                     "To add an alternative Author name, a primary author must be selected to add it to.");
            }
         }
      };
   }

   private ActionListener getMailMapGitRepoActionListener() {
      return e -> {
         JFileChooser fileChooser = new JFileChooser();
         fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
         int returnVal = fileChooser.showOpenDialog(MailMapApp.this);
         if (returnVal == JFileChooser.APPROVE_OPTION) {
            try {
               // Use the GitRepoUtils.getAuthors() method to read the selected file
               List<Author> authors = GitRepoUtils
                     .getAuthors(fileChooser.getSelectedFile().getAbsolutePath());
               // get the models for the two lists of items
               SortedListModel<Author> unassignedModel = (SortedListModel<Author>) (unassignedAuthorsList
                     .getModel());

               // check for duplicate authors in the primary model and unassigned model
               for (Author author : authors) {
                  if (!primaryAuthorSet.contains(author)
                        && !unassignedAuthorsSet.contains(author)) {
                     unassignedModel.addElement(author);
                     unassignedAuthorsSet.add(author);
                  }
               }
            } catch (IOException ex) {
               JOptionPane.showMessageDialog(MailMapApp.this, "Error reading git repo.");
            }
         }
      };
   }

   private ActionListener getMailMapLoaderActionListener() {
      return e -> {
         JFileChooser fileChooser = new JFileChooser();
         int returnVal = fileChooser.showOpenDialog(MailMapApp.this);
         if (returnVal == JFileChooser.APPROVE_OPTION) {
            try {
               // Use the MailMapEntry.readMailMap() method to read the selected file
               List<MailMapEntry> mailMapEntries = MailMapEntry
                     .readMailMap(fileChooser.getSelectedFile().getAbsolutePath());
               // Clear the unassignedAuthorsList and primaryAuthorsList
               SortedListModel<Author> unassignedModel = (SortedListModel<Author>) (unassignedAuthorsList
                     .getModel());
               unassignedModel.clear();

               DefaultTreeModel treeModel = (DefaultTreeModel) mailmapTree.getModel();
               MailMapAuthorTreeRoot root = (MailMapAuthorTreeRoot) treeModel.getRoot();
               for (MutableTreeNode node : root.getChildren()) {
                  treeModel.removeNodeFromParent(node);
               }
               primaryAuthorSet.clear();

               // Iterate through the mailMapEntries list and add the primary authors to the
               // primaryAuthorsList and the alternative authors to the unassignedAuthorsList
               for (MailMapEntry entry : mailMapEntries) {
                  MailMapAuthorTreeNode treeNode = null;
                  if (entry.getPrimary() != null) {
                     if (primaryAuthorSet.contains(entry.getPrimary())) {
                        if (entry.getSecondary() != null) {
                           treeNode = find(entry.getPrimary());
                        }
                     } else {
                        treeNode = new MailMapAuthorTreeNode(new MailMapAuthor(entry.getPrimary()),
                              getRoot());
                        primaryAuthorSet.add(entry.getPrimary());
                        treeModel.insertNodeInto(treeNode, root, root.getChildCount());
                        treeModel.nodesChanged(getRoot(),
                              IntStream.range(0, getRoot().getChildCount()).toArray());
                     }

                  }
                  if (entry.getSecondary() != null) {
                     treeNode.getMailMapAuthor().addAlternativeAuthor(entry.getSecondary());
                     treeModel.insertNodeInto(new AuthorTreeNode(entry.getSecondary(), treeNode),
                           treeNode, treeNode.getChildCount());
                     treeModel.nodesChanged(treeNode,
                           IntStream.range(0, treeNode.getChildCount()).toArray());
                  }
               }
            } catch (IOException ex) {
               JOptionPane.showMessageDialog(MailMapApp.this, "Error reading mailmap file.");
            }
         }
      };
   }

   private ActionListener getMailMapWriterActionListener() {
      return e -> {
         JFileChooser fileChooser = new JFileChooser();
         int returnVal = fileChooser.showSaveDialog(MailMapApp.this);
         if (returnVal == JFileChooser.APPROVE_OPTION) {
            try {
               // Update the mailMapEntries list with the current state of the
               // primaryAuthorsList
               List<MailMapEntry> mailMapEntries = collectAllEntries();

               // Use the MailMapEntry.writeMailMap() method to save the current state of the
               // JLists to the selected file
               MailMapEntry.writeMailMap(fileChooser.getSelectedFile().getAbsolutePath(),
                     mailMapEntries);
            } catch (IOException ex) {
               JOptionPane.showMessageDialog(MailMapApp.this, "Error writing mailmap file.");
            }
         }
      };
   }

   private ActionListener getPrimaryAuthorButtonActionListener() {
      return e -> {
         // Retrieve selected author from unassignedAuthorsList
         Author selectedAuthor = unassignedAuthorsList.getSelectedValue();
         if (selectedAuthor != null) {
            makePrimaryAddition(selectedAuthor);
         }
      };
   }

   private ActionListener getRemoveEntryButtonActionListener() {
      return e -> {
         Object selectedObj = mailmapTree.getLastSelectedPathComponent();
         if (selectedObj instanceof AuthorTreeNode) {
            AuthorTreeNode node = (AuthorTreeNode) selectedObj;
            getTreeModel().removeNodeFromParent(node);
            SortedListModel<Author> unassignedModel = (SortedListModel<Author>) (unassignedAuthorsList
                  .getModel());
            unassignedModel.addElement(node.getAuthor());

         } else if (selectedObj instanceof MailMapAuthorTreeNode) {
            MailMapAuthorTreeNode node = (MailMapAuthorTreeNode) selectedObj;
            getTreeModel().removeNodeFromParent(node);
            SortedListModel<Author> unassignedModel = (SortedListModel<Author>) (unassignedAuthorsList
                  .getModel());
            unassignedModel.addElement(node.getMailMapAuthor().getPrimaryAuthor());
            for (Author alt : node.getMailMapAuthor().getAlternativeAuthors()) {
               unassignedModel.addElement(alt);
            }
            primaryAuthorSet.remove(node.getMailMapAuthor().getPrimaryAuthor());
         }
      };
   }

   private MailMapAuthorTreeRoot getRoot() {
      return (MailMapAuthorTreeRoot) getTreeModel().getRoot();
   }

   private MailMapAuthor getSelectedPrimaryAuthor() {
      Object selectedObj = mailmapTree.getLastSelectedPathComponent();
      if (selectedObj == null) {
         return null;
      } else if (selectedObj instanceof AuthorTreeNode) {
         MailMapAuthorTreeNode node = (MailMapAuthorTreeNode) (((AuthorTreeNode) selectedObj)
               .getParent());
         return node.getMailMapAuthor();
      } else if (selectedObj instanceof MailMapAuthorTreeNode) {
         return ((MailMapAuthorTreeNode) selectedObj).getMailMapAuthor();
      }
      return null;
   }

   private DefaultTreeModel getTreeModel() {
      return (DefaultTreeModel) mailmapTree.getModel();
   }

   private void makePrimaryAddition(Author selectedAuthor) {
      // Remove the selected author from the unassignedAuthorsList
      ((SortedListModel<Author>) (unassignedAuthorsList.getModel())).removeElement(selectedAuthor);
      unassignedAuthorsSet.remove(selectedAuthor);
      if (!primaryAuthorSet.contains(selectedAuthor)) {
         // Add the selected author as a primary author to the primaryAuthorsList
         MailMapAuthorTreeNode node = new MailMapAuthorTreeNode(new MailMapAuthor(selectedAuthor),
               getRoot());
         getTreeModel().insertNodeInto(node, getRoot(), getRoot().getChildCount());
         getTreeModel().nodesChanged(getRoot(),
               IntStream.range(0, getRoot().getChildCount()).toArray());
         if (getRoot().getChildCount() > 0) {
            mailmapTree.expandPath(new TreePath(new Object[] { getRoot() }));
         }
         primaryAuthorSet.add(selectedAuthor);
      } else {
         JOptionPane.showMessageDialog(MailMapApp.this,
               "The selected author already exists as a primary author, removing from the unassigned list.");
      }
   }

   private void makeSecondaryAddition(Author selectedAuthor, MailMapAuthor selectedPrimaryAuthor) {
      // Remove the selected author from the unassignedAuthorsList
      ((SortedListModel<Author>) (unassignedAuthorsList.getModel())).removeElement(selectedAuthor);
      unassignedAuthorsSet.remove(selectedAuthor);
      // Add the selected author as a secondary author to the selected primary author
      // in primaryAuthorsList
      selectedPrimaryAuthor.addAlternativeAuthor(selectedAuthor);
      // Update the selected primary author in primaryAuthorsList
      MailMapAuthorTreeNode node = find(selectedPrimaryAuthor.getPrimaryAuthor());
      if (node != null) {
         getTreeModel().insertNodeInto(new AuthorTreeNode(selectedAuthor, node), node,
               node.getChildCount());
         getTreeModel().nodesChanged(node, IntStream.range(0, node.getChildCount()).toArray());
      }
   }

   private void setupControl() {
      // Add ActionListeners to JMenuItems
      openMenuItem.addActionListener(getMailMapLoaderActionListener());
      loadGitRepoItem.addActionListener(getMailMapGitRepoActionListener());
      saveMenuItem.addActionListener(getMailMapWriterActionListener());

      // Add ActionListeners to JButtons
      addPrimaryAuthorButton.addActionListener(getPrimaryAuthorButtonActionListener());
      addAlternativeAuthorButton.addActionListener(getAlternativeButtonActionListener());
      removeEntryButton.addActionListener(getRemoveEntryButtonActionListener());

      mailmapTree.setTransferHandler(new TreeTransferHandler());
   }

   private void setupLayout() {
      setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
      setTitle("MailMap");

      // Add JMenuBar to JFrame
      setJMenuBar(menuBar);
      menuBar.add(fileMenu);
      fileMenu.add(openMenuItem);
      fileMenu.add(loadGitRepoItem);
      fileMenu.add(saveMenuItem);

      // Create JPanel for JLists and JButtons
      JPanel listPanel = new JPanel();
      listPanel.setLayout(new GridBagLayout());
      GridBagConstraints c = new GridBagConstraints();

      // Add unassignedAuthorsList to listPanel
      c.fill = GridBagConstraints.NONE;
      c.weightx = 0;
      c.weighty = 0;
      c.gridx = 0;
      c.gridy = 0;
      listPanel.add(new JLabel("Unassigned Authors"), c);
      c.fill = GridBagConstraints.BOTH;
      c.weightx = 1;
      c.weighty = 1;
      c.gridx = 0;
      c.gridy = 1;
      listPanel.add(new JScrollPane(unassignedAuthorsList), c);
      listPanel.setPreferredSize(new Dimension(200, 300));

      // create panel for buttons
      JPanel btnPanel = new JPanel();
      btnPanel.setLayout(new GridBagLayout());
      c.fill = GridBagConstraints.VERTICAL;
      c.weightx = 0;
      c.weighty = 0;
      c.gridx = 0;
      c.gridy = 0;
      btnPanel.add(Box.createVerticalGlue(), c);
      c.fill = GridBagConstraints.HORIZONTAL;
      c.gridx = 0;
      c.gridy = 1;
      btnPanel.add(addPrimaryAuthorButton, c);
      c.gridx = 0;
      c.gridy = 2;
      btnPanel.add(Box.createVerticalStrut(10), c);
      c.gridx = 0;
      c.gridy = 3;
      btnPanel.add(addAlternativeAuthorButton, c);
      c.gridx = 0;
      c.gridy = 4;
      btnPanel.add(Box.createVerticalStrut(10), c);
      c.gridx = 0;
      c.gridy = 5;
      btnPanel.add(removeEntryButton, c);
      c.fill = GridBagConstraints.VERTICAL;
      c.gridx = 0;
      c.gridy = 4;
      btnPanel.add(Box.createVerticalGlue(), c);
      Insets margin = new Insets(1, 1, 1, 1); // top, left, bottom, right
      addPrimaryAuthorButton.setMargin(margin);
      addAlternativeAuthorButton.setMargin(margin);
      removeEntryButton.setMargin(margin);

      // Create JPanel for JTree
      JPanel treePanel = new JPanel();
      treePanel.setLayout(new GridBagLayout());

      // Add mailmapTree to treePanel
      c.fill = GridBagConstraints.NONE;
      c.weightx = 0;
      c.weighty = 0;
      c.gridx = 0;
      c.gridy = 0;
      treePanel.add(new JLabel("Assigned Authors"), c);
      c.fill = GridBagConstraints.BOTH;
      c.weightx = 1;
      c.weighty = 1;
      c.gridx = 0;
      c.gridy = 1;
      treePanel.add(new JScrollPane(mailmapTree), c);
      treePanel.setPreferredSize(new Dimension(200, 300));

      mailmapTree.setRootVisible(false);
      mailmapTree.setShowsRootHandles(true);

      // Add listPanel and treePanel to JFrame using GridBagLayout
      setLayout(new GridBagLayout());

      c.fill = GridBagConstraints.BOTH;
      c.weightx = 1;
      c.weighty = 1;
      c.gridx = 0;
      c.gridy = 0;
      add(listPanel, c);
      c.fill = GridBagConstraints.NONE;
      c.weightx = 0;
      c.weighty = 0;
      c.gridx = 1;
      c.gridy = 0;
      add(btnPanel, c);
      c.fill = GridBagConstraints.BOTH;
      c.weightx = 1;
      c.weighty = 1;
      c.gridx = 2;
      c.gridy = 0;
      add(treePanel, c);
   }
}
