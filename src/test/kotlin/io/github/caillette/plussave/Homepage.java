package io.github.caillette.plussave;
import org.openqa.selenium.By;

public class Homepage {
  // the tabs
  public static final String LOC_LNK_PROJECTSTAB = "li#menu_projects a[href$='projects/']";
  public static final String LOC_LNK_DOWNLOADTAB = "li#menu_download a[href$='download/']";
  public static final By LOC_LNK_DOCUMENTATIONTAB = By.xpath("//li[@id='menu_documentation']/a[contains(@href, 'docs/')]");
  public static final String LOC_LNK_SUPPORTTAB = "li#menu_support a[href$='support/']";
  public static final String LOC_LNK_ABOUTTAB = "li#menu_about a[href$='about/']";

  // download link
  public static final By LOC_LNK_DOWNLOADSELENIUM = By.linkText("Download Selenium");

}
