import AppShell from "@/components/AppShell";
import Footer from "@/components/Footer";

export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  return <AppShell>{children}<Footer/></AppShell>;

}
  