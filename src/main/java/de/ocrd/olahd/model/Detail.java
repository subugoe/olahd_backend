package de.ocrd.olahd.model;


import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(indexName = "detail", type = "detail")
public class Detail {

	@Field(type = FieldType.Text)
	private String PID;
	@Id
	@Field(type = FieldType.Keyword)
	private String ID;
	@Field(type = FieldType.Text)
	private String title;
	@Field(type = FieldType.Text)
	private String subtitle;
	@Field(type = FieldType.Text)
	private String placeOfPublish;
	@Field(type = FieldType.Integer)
	private int yearOfPublish;
	@Field(type = FieldType.Text)
	private int yearDigitization;
	@Field(type = FieldType.Text)
	private String publisher;
	@Field(type = FieldType.Text)
	private String creator;
	@Field(type = FieldType.Text)
	private String genre;
	@Field(type = FieldType.Text)
	private String label;
	@Field(type = FieldType.Text)
	private String classification;
	@Field(type = FieldType.Text)
	private String copyright;
	@Field(type = FieldType.Text)
	private String license;
	@Field(type = FieldType.Text)
	private String licenseURL;
	@Field(type = FieldType.Text)
	private String owner;
	@Field(type = FieldType.Text)
	private String ownerURL;
	@Field(type = FieldType.Boolean)
	private boolean isGT;
	@Field(type = FieldType.Text)
	private FileTree fileTree;
	/**
	 * If an ocrd-zip is updated with a newer version, currently the indexer replaces the search entry when the
	 * identifier in the metsfile didn't change. In this case the info for the latest version of this OCRD-ZIP should be
	 * returned and the PID for which the information was originally queried should be inserted here
	 */
	@Field(type = FieldType.Text)
	private String infoForPreviousPid;

    public Detail(
        String PID, String ID, String title, String subtitle, String placeOfPublish, int yearOfPublish,
        int yearDigitization, String publisher, String creator, String genre, String label, String classification,
        String copyright, String license, String licenseURL, String owner, String ownerURL, boolean isGT,
        FileTree fileTree, String infoForPreviousPid
    ) {
		this.PID = PID;
		this.ID = ID;
		this.title = title;
		this.subtitle = subtitle;
		this.placeOfPublish = placeOfPublish;
		this.yearOfPublish = yearOfPublish;
		this.yearDigitization = yearDigitization;
		this.publisher = publisher;
		this.creator = creator;
		this.genre = genre;
		this.label = label;
		this.classification = classification;
		this.copyright = copyright;
		this.license = license;
		this.licenseURL = licenseURL;
		this.owner = owner;
		this.ownerURL = ownerURL;
		this.isGT = isGT;
		this.fileTree = fileTree;
	}

	public Detail() {
	}

	public String getPID() {
		return PID;
	}

	public void setPID(String PID) {
		this.PID = PID;
	}

	public String getID() {
		return ID;
	}

	public void setID(String ID) {
		this.ID = ID;
	}

	public String getSubtitle() {
		return subtitle;
	}

	public void setSubtitle(String subtitle) {
		this.subtitle = subtitle;
	}

	public String getPlaceOfPublish() {
		return placeOfPublish;
	}

	public void setPlaceOfPublish(String placeOfPublish) {
		this.placeOfPublish = placeOfPublish;
	}

	public int getYearOfPublish() {
		return yearOfPublish;
	}

	public void setYearOfPublish(int yearOfPublish) {
		this.yearOfPublish = yearOfPublish;
	}

    public int getYearDigitization() {
        return yearDigitization;
    }

    public void setYearDigitization(int yearDigitization) {
        this.yearDigitization = yearDigitization;
    }
	
	public String getGenre() {
		return genre;
	}

	public void setGenre(String genre) {
		this.genre = genre;
	}

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public String getClassification() {
		return classification;
	}

	public void setClassification(String classification) {
		this.classification = classification;
	}

	public String getCopyright() {
		return copyright;
	}

	public void setCopyright(String copyright) {
		this.copyright = copyright;
	}

	public String getLicense() {
		return license;
	}

	public void setLicense(String license) {
		this.license = license;
	}

	public String getLicenseURL() {
		return licenseURL;
	}

	public void setLicenseURL(String licenseURL) {
		this.licenseURL = licenseURL;
	}

	public String getOwner() {
		return owner;
	}

	public void setOwner(String owner) {
		this.owner = owner;
	}

	public String getOwnerURL() {
		return ownerURL;
	}

	public void setOwnerURL(String ownerURL) {
		this.ownerURL = ownerURL;
	}

	public boolean isGT() {
		return isGT;
	}

	public void setGT(boolean GT) {
		isGT = GT;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getCreator() {
		return creator;
	}

	public void setCreator(String creator) {
		this.creator = creator;
	}

	public String getPublisher() {
		return publisher;
	}

	public void setPublisher(String publisher) {
		this.publisher = publisher;
	}

	public FileTree getFileTree() {
		return fileTree;
	}

	public void setFileTree(FileTree fileTree) {
		this.fileTree = fileTree;
	}

    public String getInfoForPreviousPid() {
        return infoForPreviousPid;
    }

    public void setInfoForPreviousPid(String infoForPreviousPid) {
        this.infoForPreviousPid = infoForPreviousPid;
    }
}
