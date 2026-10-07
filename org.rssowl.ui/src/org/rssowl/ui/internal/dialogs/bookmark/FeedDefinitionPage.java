/*   **********************************************************************  **
 **   Copyright notice                                                       **
 **                                                                          **
 **   (c) 2005-2009 RSSOwl Development Team                                  **
 **   http://www.rssowl.org/                                                 **
 **                                                                          **
 **   All rights reserved                                                    **
 **                                                                          **
 **   This program and the accompanying materials are made available under   **
 **   the terms of the Eclipse Public License v1.0 which accompanies this    **
 **   distribution, and is available at:                                     **
 **   http://www.rssowl.org/legal/epl-v10.html                               **
 **                                                                          **
 **   A copy is found in the file epl-v10.html and important notices to the  **
 **   license from the team is found in the textfile LICENSE.txt distributed **
 **   in this package.                                                       **
 **                                                                          **
 **   This copyright notice MUST APPEAR in all copies of the file!           **
 **                                                                          **
 **   Contributors:                                                          **
 **     RSSOwl Development Team - initial API and implementation             **
 **                                                                          **
 **  **********************************************************************  */

package org.rssowl.ui.internal.dialogs.bookmark;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.fieldassist.ContentProposalAdapter;
import org.eclipse.jface.fieldassist.SimpleContentProposalProvider;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.FontMetrics;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Text;
import org.rssowl.core.Owl;
import org.rssowl.core.internal.persist.pref.DefaultPreferences;
import org.rssowl.core.persist.IBookMark;
import org.rssowl.core.persist.ILabel;
import org.rssowl.core.persist.dao.OwlDAO;
import org.rssowl.core.persist.dao.ICategoryDAO;
import org.rssowl.core.persist.dao.ILabelDAO;
import org.rssowl.core.persist.pref.IPreferenceScope;
import org.rssowl.core.util.Pair;
import org.rssowl.core.util.StringUtils;
import org.rssowl.core.util.URIUtils;
import org.rssowl.ui.internal.OwlUI;
import org.rssowl.ui.internal.actions.ImportAction;
import org.rssowl.ui.internal.util.JobRunner;
import org.rssowl.ui.internal.util.LayoutUtils;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * @author bpasero
 */
public class FeedDefinitionPage extends WizardPage {
  private TabFolder fTabFolder;
  private Text fFeedLinkInput;
  private Text fGoogleNewsKeywordInput;
  private Text fGoogleNewsUrlPreview;
  private Text fKeywordInput;
  private Button fLoadTitleFromFeedButton;
  private String fInitialLink;
  private IPreferenceScope fGlobalScope = Owl.getPreferenceService().getGlobalScope();
  private boolean fIsAutoCompleteKeywordHooked;
  private boolean fIsAutoCompleteGoogleNewsHooked;
  private Map<String, IBookMark> fExistingFeeds = new HashMap<String, IBookMark>();

  /**
   * @param pageName
   * @param initialLink
   */
  protected FeedDefinitionPage(String pageName, String initialLink) {
    super(pageName, pageName, OwlUI.getImageDescriptor("icons/wizban/bkmrk_wiz.gif")); //$NON-NLS-1$
    setMessage(Messages.FeedDefinitionPage_CREATE_BOOKMARK);
    fInitialLink = initialLink;

    Collection<IBookMark> bookmarks = OwlDAO.loadAll(IBookMark.class);
    for (IBookMark bookMark : bookmarks) {
      fExistingFeeds.put(bookMark.getFeedLinkReference().getLinkAsText(), bookMark);
    }
  }

  boolean loadTitleFromFeed() {
    return fTabFolder != null && fTabFolder.getSelectionIndex() == 0 && fLoadTitleFromFeedButton.getSelection();
  }

  private String loadInitialLinkFromClipboard() {
    String initial = URIUtils.HTTPS;

    Clipboard cb = new Clipboard(getShell().getDisplay());
    TextTransfer transfer = TextTransfer.getInstance();
    String data = (String) cb.getContents(transfer);
    data = (data != null) ? data.trim() : null;
    cb.dispose();

    if (URIUtils.looksLikeLink(data))
      initial = URIUtils.ensureProtocol(data);

    return initial;
  }

  String getLink() {
    if (fTabFolder == null)
      return null;
    int idx = fTabFolder.getSelectionIndex();
    if (idx == 0)
      return fFeedLinkInput.getText().trim();
    if (idx == 1)
      return URIUtils.makeGoogleNewsRssUrl(fGoogleNewsKeywordInput.getText());
    return null;
  }

  void setLink(String link) {
    if (fTabFolder != null)
      fTabFolder.setSelection(0);
    fFeedLinkInput.setText(link);
    onLinkChange();
  }

  String getKeyword() {
    if (fTabFolder == null)
      return null;
    int idx = fTabFolder.getSelectionIndex();
    if (idx == 2)
      return fKeywordInput.getText();
    return null;
  }

  boolean isKeywordSubscription() {
    return fTabFolder != null && fTabFolder.getSelectionIndex() == 2 && StringUtils.isSet(fKeywordInput.getText());
  }

  boolean isGoogleNewsSubscription() {
    return fTabFolder != null && fTabFolder.getSelectionIndex() == 1 && StringUtils.isSet(fGoogleNewsKeywordInput.getText());
  }

  String getGoogleNewsKeyword() {
    return fGoogleNewsKeywordInput != null ? fGoogleNewsKeywordInput.getText().trim() : ""; //$NON-NLS-1$
  }

  /*
   * @see org.eclipse.jface.dialogs.DialogPage#setVisible(boolean)
   */
  @Override
  public void setVisible(boolean visible) {
    super.setVisible(visible);

    if (visible && fTabFolder != null) {
      int idx = fTabFolder.getSelectionIndex();
      if (idx == 0)
        fFeedLinkInput.setFocus();
      else if (idx == 1)
        fGoogleNewsKeywordInput.setFocus();
      else if (idx == 2)
        fKeywordInput.setFocus();
    }
  }

  /*
   * @see org.eclipse.jface.wizard.WizardPage#isPageComplete()
   */
  @Override
  public boolean isPageComplete() {
    if (fTabFolder == null)
      return false;

    int idx = fTabFolder.getSelectionIndex();
    if (idx == 0) {
      String link = fFeedLinkInput.getText().trim();
      return link.length() > 0 && !URIUtils.HTTP.equals(link) && !URIUtils.HTTPS.equals(link);
    }
    if (idx == 1) {
      return fGoogleNewsKeywordInput.getText().trim().length() > 0;
    }
    if (idx == 2) {
      return fKeywordInput.getText().trim().length() > 0;
    }
    return false;
  }

  /*
   * @see org.eclipse.jface.dialogs.IDialogPage#createControl(org.eclipse.swt.widgets.Composite)
   */
  @Override
  public void createControl(Composite parent) {
    Composite container = new Composite(parent, SWT.NONE);
    container.setLayout(new GridLayout(1, false));

    fTabFolder = new TabFolder(container, SWT.NONE);
    fTabFolder.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

    /* 1) Feed by Link */
    TabItem linkTab = new TabItem(fTabFolder, SWT.NONE);
    linkTab.setText(Messages.FeedDefinitionPage_TAB_LINK);

    Composite linkContainer = new Composite(fTabFolder, SWT.NONE);
    linkContainer.setLayout(new GridLayout(1, false));
    linkTab.setControl(linkContainer);

    Label linkLabel = new Label(linkContainer, SWT.NONE);
    boolean loadTitleFromFeed = fGlobalScope.getBoolean(DefaultPreferences.BM_LOAD_TITLE_FROM_FEED);
    linkLabel.setText(loadTitleFromFeed ? Messages.FeedDefinitionPage_CREATE_FEED : Messages.FeedDefinitionPage_CREATE_FEED_DIRECT);

    fFeedLinkInput = new Text(linkContainer, SWT.BORDER);
    fFeedLinkInput.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false));

    GC gc = new GC(fFeedLinkInput);
    gc.setFont(JFaceResources.getDialogFont());
    FontMetrics fontMetrics = gc.getFontMetrics();
    int entryFieldWidth = Dialog.convertHorizontalDLUsToPixels(fontMetrics, IDialogConstants.ENTRY_FIELD_WIDTH);
    gc.dispose();

    ((GridData) fFeedLinkInput.getLayoutData()).widthHint = entryFieldWidth;

    if (!StringUtils.isSet(fInitialLink))
      fInitialLink = loadInitialLinkFromClipboard();

    if (StringUtils.isSet(fInitialLink) && !fInitialLink.equals(URIUtils.HTTP) && !fInitialLink.equals(URIUtils.HTTPS)) {
      fFeedLinkInput.setText(fInitialLink);
      fFeedLinkInput.selectAll();
      onLinkChange();
    } else {
      fFeedLinkInput.setText(URIUtils.HTTPS);
      fFeedLinkInput.setSelection(URIUtils.HTTPS.length());
    }

    fFeedLinkInput.addModifyListener(new ModifyListener() {
      @Override
      public void modifyText(ModifyEvent e) {
        getContainer().updateButtons();
        onLinkChange();
      }
    });

    fLoadTitleFromFeedButton = new Button(linkContainer, SWT.CHECK);
    fLoadTitleFromFeedButton.setText(Messages.FeedDefinitionPage_USE_TITLE_OF_FEED);
    fLoadTitleFromFeedButton.setSelection(loadTitleFromFeed);
    fLoadTitleFromFeedButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        getContainer().updateButtons();
      }
    });

    /* 2) Google News Keyword */
    TabItem googleNewsTab = new TabItem(fTabFolder, SWT.NONE);
    googleNewsTab.setText(Messages.FeedDefinitionPage_TAB_GOOGLE_NEWS);

    Composite googleNewsContainer = new Composite(fTabFolder, SWT.NONE);
    googleNewsContainer.setLayout(new GridLayout(1, false));
    googleNewsTab.setControl(googleNewsContainer);

    Label gnewsLabel = new Label(googleNewsContainer, SWT.NONE);
    gnewsLabel.setText(Messages.FeedDefinitionPage_GOOGLE_NEWS_KEYWORD);

    fGoogleNewsKeywordInput = new Text(googleNewsContainer, SWT.BORDER);
    fGoogleNewsKeywordInput.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false));
    ((GridData) fGoogleNewsKeywordInput.getLayoutData()).widthHint = entryFieldWidth;

    Label previewLabel = new Label(googleNewsContainer, SWT.NONE);
    previewLabel.setText(Messages.FeedDefinitionPage_GOOGLE_NEWS_URL_PREVIEW);

    fGoogleNewsUrlPreview = new Text(googleNewsContainer, SWT.BORDER | SWT.READ_ONLY);
    fGoogleNewsUrlPreview.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false));

    fGoogleNewsKeywordInput.addModifyListener(new ModifyListener() {
      @Override
      public void modifyText(ModifyEvent e) {
        onGoogleNewsChange();
        getContainer().updateButtons();
      }
    });

    /* 3) Other Keywords */
    TabItem keywordTab = new TabItem(fTabFolder, SWT.NONE);
    keywordTab.setText(Messages.FeedDefinitionPage_TAB_KEYWORD);

    Composite keywordContainer = new Composite(fTabFolder, SWT.NONE);
    keywordContainer.setLayout(new GridLayout(1, false));
    keywordTab.setControl(keywordContainer);

    Label keywordLabel = new Label(keywordContainer, SWT.NONE);
    keywordLabel.setText(Messages.FeedDefinitionPage_CREATE_KEYWORD_FEED);

    fKeywordInput = new Text(keywordContainer, SWT.BORDER);
    fKeywordInput.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false));
    ((GridData) fKeywordInput.getLayoutData()).widthHint = entryFieldWidth;
    fKeywordInput.addModifyListener(new ModifyListener() {
      @Override
      public void modifyText(ModifyEvent e) {
        getContainer().updateButtons();
      }
    });

    /* Tab selection */
    fTabFolder.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        int idx = fTabFolder.getSelectionIndex();
        if (idx == 0) {
          fFeedLinkInput.setFocus();
          onLinkChange();
        } else if (idx == 1) {
          fGoogleNewsKeywordInput.setFocus();
          hookGoogleNewsAutocomplete();
          onGoogleNewsChange();
        } else if (idx == 2) {
          fKeywordInput.setFocus();
          hookKeywordAutocomplete();
          setMessage(Messages.FeedDefinitionPage_CREATE_BOOKMARK);
        }
        getContainer().updateButtons();
      }
    });

    /* Info Container */
    Composite infoContainer = new Composite(container, SWT.NONE);
    infoContainer.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
    infoContainer.setLayout(LayoutUtils.createGridLayout(2, 0, 5));

    Label infoImg = new Label(infoContainer, SWT.NONE);
    infoImg.setImage(OwlUI.getImage(infoImg, "icons/obj16/info.gif")); //$NON-NLS-1$
    infoImg.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));

    Link infoLink = new Link(infoContainer, SWT.NONE);
    infoLink.setText(Messages.FeedDefinitionPage_IMPORT_WIZARD_TIP);
    infoLink.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    infoLink.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        new ImportAction().openWizardForKeywordSearch(getShell());
      }
    });

    Dialog.applyDialogFont(container);

    setControl(container);
    fFeedLinkInput.setFocus();
  }

  private void onGoogleNewsChange() {
    String keyword = fGoogleNewsKeywordInput.getText();
    String generatedUrl = URIUtils.makeGoogleNewsRssUrl(keyword);
    fGoogleNewsUrlPreview.setText(generatedUrl);

    if (StringUtils.isSet(generatedUrl)) {
      IBookMark existingBookMark = fExistingFeeds.get(generatedUrl);
      if (existingBookMark != null)
        setMessage(NLS.bind(Messages.FeedDefinitionPage_BOOKMARK_EXISTS, existingBookMark.getName()), WARNING);
      else
        setMessage(Messages.FeedDefinitionPage_CREATE_BOOKMARK);
    } else {
      setMessage(Messages.FeedDefinitionPage_CREATE_BOOKMARK);
    }
  }

  private void onLinkChange() {
    IBookMark existingBookMark = fExistingFeeds.get(fFeedLinkInput.getText());

    if (existingBookMark != null)
      setMessage(NLS.bind(Messages.FeedDefinitionPage_BOOKMARK_EXISTS, existingBookMark.getName()), WARNING);
    else
      setMessage(Messages.FeedDefinitionPage_CREATE_BOOKMARK);
  }

  private void hookGoogleNewsAutocomplete() {
    if (fIsAutoCompleteGoogleNewsHooked)
      return;
    fIsAutoCompleteGoogleNewsHooked = true;

    final Pair<SimpleContentProposalProvider, ContentProposalAdapter> autoComplete = OwlUI.hookAutoComplete(fGoogleNewsKeywordInput, null, true, false);

    JobRunner.runInBackgroundThread(new Runnable() {
      @Override
      public void run() {
        if (!fGoogleNewsKeywordInput.isDisposed()) {
          Set<String> values = new TreeSet<String>(new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
              return o1.compareToIgnoreCase(o2);
            }
          });

          values.addAll(OwlDAO.getDAO(ICategoryDAO.class).loadAllNames());

          Collection<ILabel> labels = OwlDAO.getDAO(ILabelDAO.class).loadAll();
          for (ILabel label : labels) {
            values.add(label.getName());
          }

          if (!fGoogleNewsKeywordInput.isDisposed())
            OwlUI.applyAutoCompleteProposals(values, autoComplete.getFirst(), autoComplete.getSecond(), false);
        }
      }
    });
  }

  private void hookKeywordAutocomplete() {

    /* Only perform once */
    if (fIsAutoCompleteKeywordHooked)
      return;
    fIsAutoCompleteKeywordHooked = true;

    final Pair<SimpleContentProposalProvider, ContentProposalAdapter> autoComplete = OwlUI.hookAutoComplete(fKeywordInput, null, true, false);

    /* Load proposals in the Background */
    JobRunner.runInBackgroundThread(new Runnable() {
      @Override
      public void run() {
        if (!fKeywordInput.isDisposed()) {
          Set<String> values = new TreeSet<String>(new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
              return o1.compareToIgnoreCase(o2);
            }
          });

          values.addAll(OwlDAO.getDAO(ICategoryDAO.class).loadAllNames());

          Collection<ILabel> labels = OwlDAO.getDAO(ILabelDAO.class).loadAll();
          for (ILabel label : labels) {
            values.add(label.getName());
          }

          /* Apply Proposals */
          if (!fKeywordInput.isDisposed())
            OwlUI.applyAutoCompleteProposals(values, autoComplete.getFirst(), autoComplete.getSecond(), false);
        }
      }
    });
  }
}