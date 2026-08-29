package gitrepo

import (
	"testing"

	"privateclouddisk/git-service/internal/domain"
)

func TestValidateRefName(t *testing.T) {
	valid := []string{"main", "feature/login", "release-2026.08"}
	for _, value := range valid {
		if err := ValidateRefName(value); err != nil {
			t.Fatalf("expected %q to be valid: %v", value, err)
		}
	}
	invalid := []string{"../main", "main..broken", "/main", "main/", "main.lock", "refs//heads/main"}
	for _, value := range invalid {
		if err := ValidateRefName(value); err == nil {
			t.Fatalf("expected %q to be rejected", value)
		}
	}
}

func TestBrowserTextPreviewClassificationAndLineTruncation(t *testing.T) {
	if !isLikelyTextPath("src/app.ts") || !isTextPreviewMIME("application/json; charset=utf-8") {
		t.Fatal("expected source and JSON files to be classified as text previews")
	}
	if isLikelyTextPath("asset/manual.pdf") || isTextPreviewMIME("application/pdf") {
		t.Fatal("expected PDF to use the raw binary preview path")
	}
	content, truncated := truncatePreviewLines([]byte("one\ntwo\nthree\n"), 2)
	if !truncated || string(content) != "one\ntwo\n" {
		t.Fatalf("unexpected line truncation result: %q, truncated=%v", content, truncated)
	}
}

func TestParseRepositoryInsights(t *testing.T) {
	contributors := parseContributorStats([]byte("  7\tAlice Example <alice@example.com>\n  2\tBob <bob@example.com>\n"))
	if len(contributors) != 2 || contributors[0].Name != "Alice Example" || contributors[0].Commits != 7 {
		t.Fatalf("unexpected contributors: %#v", contributors)
	}
	languages := parseLanguageStats([]byte("100644 blob abc 120\tsrc/main.go\n100644 blob def 80\tweb/app.ts\n100644 blob ghi 20\tREADME.md\n"))
	expected := []domain.LanguageStat{{Name: "Go", Bytes: 120}, {Name: "TypeScript", Bytes: 80}, {Name: "Markdown", Bytes: 20}}
	if len(languages) != len(expected) {
		t.Fatalf("expected %d languages, got %#v", len(expected), languages)
	}
	for index, item := range expected {
		if languages[index] != item {
			t.Fatalf("language %d expected %#v, got %#v", index, item, languages[index])
		}
	}
}
