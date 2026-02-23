// Carbon Works — Branded Document Template for Typst
//
// ╔═══════════════════════════════════════════════════════════════════════════╗
// ║  ⚠️  AI ASSISTANT: ASK BEFORE EDITING THIS FILE                           ║
// ║                                                                           ║
// ║  This file is part of a 3-file PDF generation system:                     ║
// ║    1. generate-pdf.sh    — entry point, sets up paths and variables       ║
// ║    2. carbonworks.typst  — pandoc wrapper, imports conf() and invokes it  ║
// ║    3. carbonworks.typ    — THIS FILE, defines conf() function only        ║
// ║                                                                           ║
// ║  Critical rules:                                                          ║
// ║    • Do NOT add #show: conf.with(...) here — the wrapper handles that     ║
// ║    • Do NOT add $body$ here — the wrapper handles that                    ║
// ║    • Do NOT run pandoc with --template=carbonworks.typ directly           ║
// ║    • Always use generate-pdf.sh or --template=carbonworks.typst           ║
// ║                                                                           ║
// ║  Before making changes, please:                                           ║
// ║    1. Explain what you want to change and why                             ║
// ║    2. Confirm with the user which file(s) need modification               ║
// ║    3. Test with generate-pdf.sh after changes                             ║
// ╚═══════════════════════════════════════════════════════════════════════════╝
//
// Brand colors from /brand/carbon-works-brand-guidelines.md

#let deep-ink = rgb("#2D3748")
#let soft-gray = rgb("#718096")
#let light-gray = rgb("#E2E8F0")
#let warm-white = rgb("#FFFAF5")
#let sky-blue = rgb("#4A9FD4")
#let soft-teal = rgb("#38B2AC")
#let dark-teal = rgb("#03767B")
#let teal = rgb("#04949A")
#let deep-orange = rgb("#F06607")

#let conf(
  title: none,
  subtitle: none,
  authors: (),
  date: none,
  emblem: none,
  wordmark: none,
  client: none,
  confidential: false,
  cols: 1,
  doc,
) = {

  // ── Page setup ──
  set page(
    paper: "us-letter",
    margin: (top: 1in, bottom: 1in, left: 1in, right: 1in),
  )

  // ── Base typography ──
  set text(
    font: ("Inter", "Calibri", "Arial"),
    size: 11pt,
    fill: deep-ink,
  )
  set par(
    leading: 0.75em,
    justify: true,
  )

  // ── Cover page ──
  {
    set page(
      header: none,
      footer: none,
      margin: (top: 1.5in, bottom: 1.5in, left: 1.25in, right: 1.25in),
    )
    set par(justify: false)

    // Wordmark at bottom-left (above confidentiality notice)
    if wordmark != none {
      place(
        bottom + left,
        dy: -0.5in,
        dx: 0in,
        box(stroke: none, image(wordmark, width: 2.25in)),
      )
    }

    // Emblem above title
    if emblem != none {
      image(emblem, height: 0.8in)
      v(0.3in)
    } else {
      v(1.25in)
    }

    // Title
    if title != none {
      text(
        size: 28pt,
        weight: "bold",
        fill: dark-teal,
        title,
      )
      v(0.3em)
    }

    // Subtitle
    if subtitle != none {
      text(
        size: 14pt,
        fill: soft-gray,
        style: "italic",
        subtitle,
      )
      v(0.6em)
    }

    // Accent line
    line(length: 3in, stroke: 2pt + deep-orange)

    v(1em)

    // Client line
    if client != none {
      text(
        size: 12pt,
        fill: soft-gray,
      )[Prepared for: #text(fill: deep-ink, weight: "medium", client)]
      v(0.5em)
    }

    // Authors (receives dictionaries with name/affiliation/email from pandoc wrapper)
    for author in authors {
      if "name" in author {
        text(size: 12pt, fill: deep-ink, weight: "medium", author.name)
        if "affiliation" in author and author.affiliation != "" and author.affiliation != [] {
          text(size: 12pt, fill: soft-gray)[ · ]
          text(size: 12pt, fill: soft-gray, author.affiliation)
        }
      }
      linebreak()
    }

    v(0.5em)

    // Date
    if date != none {
      text(size: 11pt, fill: soft-gray, date)
    }

    // Confidentiality notice
    if confidential {
      v(1fr)
      text(
        size: 9pt,
        fill: soft-gray,
        style: "italic",
      )[This document is confidential and intended solely for the named recipient.]
    }

    pagebreak()
  }

  // ── Header and footer for body pages ──
  set page(
    header: {
      set text(size: 9pt, fill: soft-gray)
      if title != none { title } else { "" }
      h(1fr)
      text(fill: soft-gray)[Carbon Works]
      v(4pt)
      line(length: 100%, stroke: 0.5pt + light-gray)
    },
    footer: {
      line(length: 100%, stroke: 0.5pt + light-gray)
      v(4pt)
      set text(size: 9pt, fill: soft-gray)
      if confidential [Confidential] else []
      h(1fr)
      context counter(page).display("1")
    },
  )

  // Reset page counter for body
  counter(page).update(1)

  // ── Heading styles ──
  show heading.where(level: 1): it => {
    v(0.8em)
    block(width: 100%)[
      #text(size: 18pt, weight: "bold", fill: dark-teal, it.body)
      #v(4pt)
      #line(length: 100%, stroke: 1.5pt + sky-blue)
    ]
    v(0.4em)
  }

  show heading.where(level: 2): it => {
    v(0.6em)
    text(size: 14pt, weight: "bold", fill: dark-teal, it.body)
    v(0.3em)
  }

  show heading.where(level: 3): it => {
    v(0.5em)
    text(size: 12pt, weight: "semibold", fill: deep-ink, it.body)
    v(0.2em)
  }

  // ── Links ──
  show link: it => {
    text(fill: sky-blue, it)
  }

  // ── Horizontal rules ──
  show line: it => {
    // Only style explicit horizontal rules (full-width lines not from headings)
    it
  }

  // ── Tables ──
  set table(
    stroke: 0.5pt + soft-teal.lighten(60%),
    inset: 8pt,
    fill: (_, row) => {
      if row == 0 { dark-teal }
      else if calc.odd(row) { white }
      else { soft-teal.lighten(85%) }
    },
  )
  show table.cell.where(y: 0): set text(fill: white, weight: "bold", size: 10pt)
  show table.cell: it => {
    set text(size: 10pt, hyphenate: false)
    set par(justify: false)
    it
  }

  // ── Code blocks ──
  show raw.where(block: true): it => {
    block(
      width: 100%,
      fill: warm-white,
      inset: 12pt,
      radius: 4pt,
      stroke: 0.5pt + light-gray,
      it,
    )
  }
  set raw(
    theme: none,
  )
  show raw: set text(font: ("Consolas", "Courier New"), size: 10pt)

  // ── Emphasis styling ──
  show emph: set text(fill: soft-gray)

  // ── Strong in body ──
  show strong: set text(fill: deep-ink)

  // ── Body ──
  if cols > 1 {
    columns(cols, doc)
  } else {
    doc
  }
}
