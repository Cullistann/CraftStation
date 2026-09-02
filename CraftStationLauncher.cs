using System;
using System.Diagnostics;
using System.IO;
using System.Windows.Forms;

class CraftStationLauncher {
    [STAThread]
    static void Main() {
        try {
            string baseDir = AppDomain.CurrentDomain.BaseDirectory;
            string panelDir = Path.Combine(baseDir, "panel");
            string javaExe = Path.Combine(baseDir, "java", "bin", "javaw.exe");
            
            if (!File.Exists(javaExe)) {
                javaExe = Path.Combine(panelDir, "..", "java", "bin", "javaw.exe");
            }
            if (!File.Exists(javaExe)) {
                javaExe = "javaw.exe";
            }

            ProcessStartInfo psi = new ProcessStartInfo();
            psi.FileName = javaExe;
            psi.Arguments = "-cp \"out;lib\\*\" Main";
            psi.WorkingDirectory = panelDir;
            psi.UseShellExecute = false;
            psi.CreateNoWindow = true;
            psi.WindowStyle = ProcessWindowStyle.Hidden;
            Process.Start(psi);
        } catch (Exception ex) {
            MessageBox.Show("CraftStation başlatılamadı:\n" + ex.Message, "CraftStation Hata", MessageBoxButtons.OK, MessageBoxIcon.Error);
        }
    }
}
