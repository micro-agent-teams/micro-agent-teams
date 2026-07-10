import { useNavigate } from "react-router";
import { useAuth } from "@/hooks/useAuth";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

export function HomePage() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  async function onLogout() {
    await logout();
    navigate("/login");
  }

  if (!user) return null;

  return (
    <div className="flex min-h-svh items-center justify-center p-4">
      <Card className="w-full max-w-md">
        <CardHeader>
          <CardTitle className="text-lg">$ whoami</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          <pre className="bg-muted overflow-x-auto rounded-md p-4 text-sm">
            {JSON.stringify(user, null, 2)}
          </pre>
          <Button variant="secondary" onClick={onLogout}>
            log out
          </Button>
        </CardContent>
      </Card>
    </div>
  );
}
