/* ###
 * IP: GHIDRA
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package docking;

import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import javax.swing.tree.TreeCellRenderer;

/**
 * 界面汉化：运行时把界面上显示的英文文本按词典替换为中文。
 * <p>
 * Ghidra 没有 i18n 框架，界面文本全部硬编码在源码里。这里不改动任何业务代码里的文本
 * （避免破坏配置键、快捷键、存档兼容），而是在显示层统一翻译：定期遍历所有已显示窗口里的
 * 组件，把词典里存在的文本替换成中文。词典为资源文件 {@code zh_CN_dict.tsv}（英文 TAB 中文）。
 * <p>
 * 可用 {@code -Dghidra.zh=false} 关闭。
 */
public final class ZhTranslator {

	private static final String DICT_RESOURCE = "/zh_CN_dict.tsv";
	private static final int SCAN_PERIOD_MS = 300;
	private static final int MAX_TEXT_AREA_LENGTH = 800;

	private static final Map<String, String> DICT = new HashMap<>();
	// 拼接片段：以空格/冒号/引号结尾的做前缀匹配，以空格/标点开头的做后缀匹配
	private static final Map<String, List<String>> PREFIX_FRAGS = new HashMap<>();
	private static final Map<String, List<String>> SUFFIX_FRAGS = new HashMap<>();
	private static final Map<String, String> MEMO = new ConcurrentHashMap<>();

	private static boolean installed;

	private ZhTranslator() {
		// utility
	}

	/** 安装翻译器，重复调用无副作用 */
	public static synchronized void install() {
		if (installed) {
			return;
		}
		installed = true;
		if ("false".equalsIgnoreCase(System.getProperty("ghidra.zh"))) {
			return;
		}
		try {
			loadDictionary();
		}
		catch (Exception e) {
			System.err.println("汉化词典加载失败: " + e);
			return;
		}
		if (DICT.isEmpty()) {
			return;
		}
		SwingUtilities.invokeLater(() -> {
			javax.swing.Timer timer = new javax.swing.Timer(SCAN_PERIOD_MS, e -> {
				try {
					scanAll();
				}
				catch (Throwable t) {
					// 翻译失败绝不能影响正常界面
				}
			});
			timer.setCoalesce(true);
			timer.start();
		});
	}

	// ---------------------------------------------------------------- 词典

	private static void loadDictionary() throws IOException {
		try (InputStream in = ZhTranslator.class.getResourceAsStream(DICT_RESOURCE)) {
			if (in == null) {
				return;
			}
			BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
			String line;
			while ((line = r.readLine()) != null) {
				if (line.isEmpty() || line.charAt(0) == '#') {
					continue;
				}
				int tab = line.indexOf('\t');
				if (tab <= 0) {
					continue;
				}
				String en = unescape(line.substring(0, tab));
				String zh = unescape(line.substring(tab + 1));
				if (en.isEmpty() || zh.isEmpty() || en.equals(zh)) {
					continue;
				}
				DICT.put(en, zh);
				registerFragment(en);
			}
		}
	}

	private static void registerFragment(String en) {
		if (en.length() < 5) {
			return;
		}
		char first = en.charAt(0);
		char last = en.charAt(en.length() - 1);
		if (last == ' ' || last == ':' || last == '\'' || last == '"' || last == '(' ||
			last == '[') {
			PREFIX_FRAGS.computeIfAbsent(en.substring(0, 4), k -> new ArrayList<>()).add(en);
		}
		if (first == ' ' || first == '\'' || first == '"' || first == ',' || first == ')' ||
			first == ':') {
			SUFFIX_FRAGS.computeIfAbsent(en.substring(en.length() - 4), k -> new ArrayList<>())
					.add(en);
		}
	}

	private static String unescape(String s) {
		if (s.indexOf('\\') < 0) {
			return s;
		}
		StringBuilder sb = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '\\' && i + 1 < s.length()) {
				char n = s.charAt(++i);
				switch (n) {
					case 'n':
						sb.append('\n');
						break;
					case 't':
						sb.append('\t');
						break;
					case 'r':
						sb.append('\r');
						break;
					case '\\':
						sb.append('\\');
						break;
					default:
						sb.append('\\').append(n);
				}
			}
			else {
				sb.append(c);
			}
		}
		return sb.toString();
	}

	// ---------------------------------------------------------------- 翻译

	/**
	 * 翻译一段界面文本；没有对应译文时原样返回。
	 * @param s 原文
	 * @return 译文
	 */
	public static String tr(String s) {
		if (s == null || s.length() < 2 || DICT.isEmpty()) {
			return s;
		}
		String cached = MEMO.get(s);
		if (cached != null) {
			return cached;
		}
		String out;
		try {
			out = translateUncached(s);
		}
		catch (Throwable t) {
			out = s;
		}
		if (MEMO.size() > 60000) {
			MEMO.clear();
		}
		MEMO.put(s, out);
		return out;
	}

	private static String translateUncached(String s) {
		if (hasCjk(s) && s.indexOf('<') < 0) {
			return s;
		}
		String lower = s.length() > 6 ? s.substring(0, 6).toLowerCase(Locale.ROOT) : "";
		if (lower.equals("<html>")) {
			return translateHtml(s);
		}
		return translatePlain(s, 0);
	}

	private static boolean hasCjk(String s) {
		for (int i = 0; i < s.length(); i++) {
			if (s.charAt(i) >= 0x2E80) {
				return true;
			}
		}
		return false;
	}

	private static String translatePlain(String s, int depth) {
		if (s.isEmpty() || depth > 6) {
			return s;
		}
		String exact = DICT.get(s);
		if (exact != null) {
			return exact;
		}
		// 多行：逐行翻译
		if (s.indexOf('\n') >= 0) {
			String[] lines = s.split("\n", -1);
			StringBuilder sb = new StringBuilder(s.length());
			for (int i = 0; i < lines.length; i++) {
				if (i > 0) {
					sb.append('\n');
				}
				sb.append(translatePlain(lines[i], depth + 1));
			}
			return sb.toString();
		}
		// 首尾空白
		int a = 0;
		int b = s.length();
		while (a < b && Character.isWhitespace(s.charAt(a))) {
			a++;
		}
		while (b > a && Character.isWhitespace(s.charAt(b - 1))) {
			b--;
		}
		if (a > 0 || b < s.length()) {
			if (a == b) {
				return s;
			}
			return s.substring(0, a) + translatePlain(s.substring(a, b), depth + 1) +
				s.substring(b);
		}
		// 结尾 ... : …
		String[] tails = { "...", "…", ":", ".", "?", "!" };
		for (String t : tails) {
			if (s.length() > t.length() + 1 && s.endsWith(t)) {
				String head = s.substring(0, s.length() - t.length());
				String r = DICT.get(head);
				if (r != null) {
					return r + (t.equals(":") ? "：" : t.equals(".") ? "。" : t);
				}
			}
		}
		// 前缀片段
		if (s.length() > 5) {
			List<String> cands = PREFIX_FRAGS.get(s.substring(0, 4));
			if (cands != null) {
				String best = null;
				for (String k : cands) {
					if (s.length() > k.length() && s.startsWith(k) &&
						(best == null || k.length() > best.length())) {
						best = k;
					}
				}
				if (best != null) {
					return DICT.get(best) + translatePlain(s.substring(best.length()), depth + 1);
				}
			}
			List<String> scands = SUFFIX_FRAGS.get(s.substring(s.length() - 4));
			if (scands != null) {
				String best = null;
				for (String k : scands) {
					if (s.length() > k.length() && s.endsWith(k) &&
						(best == null || k.length() > best.length())) {
						best = k;
					}
				}
				if (best != null) {
					return translatePlain(s.substring(0, s.length() - best.length()), depth + 1) +
						DICT.get(best);
				}
			}
		}
		return s;
	}

	private static String translateHtml(String s) {
		StringBuilder out = new StringBuilder(s.length() + 16);
		int i = 0;
		int n = s.length();
		boolean changed = false;
		while (i < n) {
			char c = s.charAt(i);
			if (c == '<') {
				int e = s.indexOf('>', i);
				if (e < 0) {
					out.append(s, i, n);
					break;
				}
				out.append(s, i, e + 1);
				i = e + 1;
			}
			else {
				int e = s.indexOf('<', i);
				if (e < 0) {
					e = n;
				}
				String text = s.substring(i, e);
				String plain = text.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
						.replace("&nbsp;", " ").replace("&amp;", "&");
				String t = translatePlain(plain, 0);
				if (!t.equals(plain)) {
					changed = true;
					out.append(t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"));
				}
				else {
					out.append(text);
				}
				i = e;
			}
		}
		return changed ? out.toString() : s;
	}

	// ---------------------------------------------------------------- 遍历

	private static void scanAll() {
		for (Window w : Window.getWindows()) {
			if (!w.isShowing()) {
				continue;
			}
			if (w instanceof Frame) {
				Frame f = (Frame) w;
				String t = f.getTitle();
				String z = tr(t);
				if (z != t && !z.equals(t)) {
					f.setTitle(z);
				}
			}
			else if (w instanceof Dialog) {
				Dialog d = (Dialog) w;
				String t = d.getTitle();
				String z = tr(t);
				if (z != t && !z.equals(t)) {
					d.setTitle(z);
				}
			}
			visit(w, 0);
		}
	}

	private static void visit(Component c, int depth) {
		if (c == null || depth > 60) {
			return;
		}
		if (c instanceof JComponent) {
			JComponent jc = (JComponent) c;
			String tip = jc.getToolTipText();
			if (tip != null) {
				String z = tr(tip);
				if (!z.equals(tip)) {
					jc.setToolTipText(z);
				}
			}
			translateBorder(jc.getBorder());
		}
		if (c instanceof JLabel) {
			JLabel l = (JLabel) c;
			String t = l.getText();
			if (t != null) {
				String z = tr(t);
				if (!z.equals(t)) {
					l.setText(z);
				}
			}
		}
		else if (c instanceof AbstractButton) {
			AbstractButton b = (AbstractButton) c;
			String t = b.getText();
			if (t != null) {
				String z = tr(t);
				if (!z.equals(t)) {
					b.setText(z);
				}
			}
			if (c instanceof JMenu) {
				for (Component mc : ((JMenu) c).getMenuComponents()) {
					visit(mc, depth + 1);
				}
			}
		}
		else if (c instanceof JTabbedPane) {
			JTabbedPane tp = (JTabbedPane) c;
			for (int i = 0; i < tp.getTabCount(); i++) {
				String t = tp.getTitleAt(i);
				String z = tr(t);
				if (t != null && !z.equals(t)) {
					tp.setTitleAt(i, z);
				}
			}
		}
		else if (c instanceof JTextArea) {
			JTextArea ta = (JTextArea) c;
			if (!ta.isEditable() && ta.getDocument().getLength() < MAX_TEXT_AREA_LENGTH) {
				String t = ta.getText();
				String z = tr(t);
				if (t != null && !z.equals(t)) {
					ta.setText(z);
				}
			}
		}
		else if (c instanceof JTable) {
			translateTableHeader((JTable) c);
		}
		else if (c instanceof JTree) {
			JTree tree = (JTree) c;
			TreeCellRenderer r = tree.getCellRenderer();
			if (r != null && !(r instanceof TreeWrap)) {
				tree.setCellRenderer(new TreeWrap(r));
			}
		}
		else if (c instanceof JList) {
			@SuppressWarnings("unchecked")
			JList<Object> list = (JList<Object>) c;
			ListCellRenderer<? super Object> r = list.getCellRenderer();
			if (r != null && !(r instanceof ListWrap) && !isFileChooser(r)) {
				list.setCellRenderer(new ListWrap(r));
			}
		}
		else if (c instanceof JComboBox) {
			@SuppressWarnings("unchecked")
			JComboBox<Object> combo = (JComboBox<Object>) c;
			ListCellRenderer<? super Object> r = combo.getRenderer();
			if (r != null && !(r instanceof ListWrap) && !isFileChooser(r)) {
				combo.setRenderer(new ListWrap(r));
			}
		}

		if (c instanceof Container) {
			for (Component child : ((Container) c).getComponents()) {
				visit(child, depth + 1);
			}
		}
	}

	private static boolean isFileChooser(Object r) {
		return r.getClass().getName().startsWith("docking.widgets.filechooser");
	}

	private static void translateBorder(Border b) {
		if (b instanceof TitledBorder) {
			TitledBorder tb = (TitledBorder) b;
			String t = tb.getTitle();
			if (t != null) {
				String z = tr(t);
				if (!z.equals(t)) {
					tb.setTitle(z);
				}
			}
		}
		else if (b instanceof CompoundBorder) {
			translateBorder(((CompoundBorder) b).getOutsideBorder());
			translateBorder(((CompoundBorder) b).getInsideBorder());
		}
	}

	private static void translateTableHeader(JTable table) {
		JTableHeader header = table.getTableHeader();
		if (header == null) {
			return;
		}
		boolean changed = false;
		for (Enumeration<TableColumn> en = table.getColumnModel().getColumns(); en
				.hasMoreElements();) {
			TableColumn col = en.nextElement();
			Object v = col.getHeaderValue();
			if (v instanceof String) {
				String z = tr((String) v);
				if (!z.equals(v)) {
					col.setHeaderValue(z);
					changed = true;
				}
			}
		}
		if (changed) {
			header.repaint();
		}
	}

	private static void translateRendered(Component c) {
		if (c instanceof JLabel) {
			JLabel l = (JLabel) c;
			String t = l.getText();
			if (t != null) {
				String z = tr(t);
				if (!z.equals(t)) {
					l.setText(z);
				}
			}
		}
	}

	// ---------------------------------------------------------------- 渲染器包装

	private static final class TreeWrap implements TreeCellRenderer {
		private final TreeCellRenderer delegate;

		TreeWrap(TreeCellRenderer delegate) {
			this.delegate = delegate;
		}

		@Override
		public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected,
				boolean expanded, boolean leaf, int row, boolean hasFocus) {
			Component c = delegate.getTreeCellRendererComponent(tree, value, selected, expanded,
				leaf, row, hasFocus);
			translateRendered(c);
			return c;
		}
	}

	private static final class ListWrap implements ListCellRenderer<Object> {
		private final ListCellRenderer<? super Object> delegate;

		ListWrap(ListCellRenderer<? super Object> delegate) {
			this.delegate = delegate;
		}

		@Override
		public Component getListCellRendererComponent(JList<? extends Object> list, Object value,
				int index, boolean isSelected, boolean cellHasFocus) {
			Component c =
				delegate.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
			translateRendered(c);
			return c;
		}
	}
}
